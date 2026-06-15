package com.hris.knowledgesearch.infrastructure.etl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.knowledgesearch.application.knowledge.command.IngestKnowledgeCommand;
import com.hris.knowledgesearch.application.knowledge.port.SettlementSourceAcl;
import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecord;
import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecordRepository;
import com.hris.knowledgesearch.infrastructure.glue.GluePartitionRegistrationListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.builder.SimpleJobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.json.JacksonJsonObjectReader;
import org.springframework.batch.item.json.JsonItemReader;
import org.springframework.batch.item.json.builder.JsonItemReaderBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 정산 지식 적재 잡 (Spring Batch, PRD §7).
 * <p>
 * 파이프라인: 읽기(JSON) → 정제·검증(processor) → 중복 제거(content_hash) → 쓰기(insert-or-skip).
 * <ul>
 *   <li>읽기: {@code classpath:sample/settlement-source.json} (의도적 공백/대소문자/중복 포함)</li>
 *   <li>처리: (1) 필수 필드 검증 — title/body 누락 시 null 반환으로 skip + 로그,
 *            (2) 문자열 정규화(trim/공백 축약/전각·반각), (3) SHA-256 content_hash 생성</li>
 *   <li>쓰기: content_hash 중복이면 skip, 아니면 INSERT (insert-or-skip)</li>
 * </ul>
 * <p>
 * 쓰기 대상은 프로파일이 정한다 — local 은 H2(JPA 어댑터), redshift 는 Redshift 네이티브 테이블
 * (JdbcTemplate 어댑터). redshift 프로파일에서는 잡이 COMPLETED 로 끝나면
 * {@link GluePartitionRegistrationListener} 가 Glue Data Catalog 에 당일 파티션을 등록해(PRD §4.2/§7)
 * 레이크 파이프라인이 쓴 당일 아카이브 파티션이 Spectrum 조회에 노출되게 한다(이 ETL 산출물은
 * 네이티브 테이블 — 파티션 데이터 생산 주체와 구분). local 은 리스너 빈이 없어 동작이 그대로다.
 */
@Slf4j
@Configuration
public class IngestionJobConfig {

    /** Spring Batch 잡 이름 (EtlController/테스트에서 참조) */
    public static final String JOB_NAME = "settlementIngestionJob";
    private static final int CHUNK_SIZE = 100;

    @Bean
    public Job settlementIngestionJob(JobRepository jobRepository, Step settlementIngestionStep,
                                      ObjectProvider<GluePartitionRegistrationListener> gluePartitionListener) {
        SimpleJobBuilder builder = new JobBuilder(JOB_NAME, jobRepository)
                .start(settlementIngestionStep);
        // redshift 프로파일에서만 빈이 존재 — local 은 그대로 빈 없이 진행한다.
        gluePartitionListener.ifAvailable(builder::listener);
        return builder.build();
    }

    @Bean
    public Step settlementIngestionStep(JobRepository jobRepository,
                                        PlatformTransactionManager transactionManager,
                                        JsonItemReader<SettlementSourceItem> settlementSourceReader,
                                        ItemProcessor<SettlementSourceItem, KnowledgeRecord> settlementProcessor,
                                        ItemWriter<KnowledgeRecord> settlementWriter) {
        return new StepBuilder("settlementIngestionStep", jobRepository)
                .<SettlementSourceItem, KnowledgeRecord>chunk(CHUNK_SIZE, transactionManager)
                .reader(settlementSourceReader)
                .processor(settlementProcessor)
                .writer(settlementWriter)
                .build();
    }

    /**
     * JSON 배열을 SettlementSourceItem 으로 읽는 리더.
     * <p>
     * 소스 경로는 {@code etl.source-resource}(기본 {@code sample/settlement-source.json})로 설정 가능 —
     * 평가용 대량 코퍼스({@code sample/settlement-source-large.json}) 적재에 쓴다.
     */
    @Bean
    public JsonItemReader<SettlementSourceItem> settlementSourceReader(
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Value("${etl.source-resource:sample/settlement-source.json}")
            String sourceResource) {
        return new JsonItemReaderBuilder<SettlementSourceItem>()
                .name("settlementSourceReader")
                .jsonObjectReader(new JacksonJsonObjectReader<>(objectMapper, SettlementSourceItem.class))
                .resource(new ClassPathResource(sourceResource))
                .build();
    }

    /**
     * 정제·검증·해시 프로세서.
     * <p>
     * 외부 소스 → 도메인 번역은 ACL({@link SettlementSourceAcl})에 위임한다. ACL 이 빈(skip)을 돌려주면
     * (필수 필드 누락) null 반환으로 해당 아이템을 필터링하고 사유를 남긴다.
     */
    @Bean
    public ItemProcessor<SettlementSourceItem, KnowledgeRecord> settlementProcessor(SettlementSourceAcl settlementSourceAcl) {
        return item -> {
            IngestKnowledgeCommand cmd = settlementSourceAcl.toIngestCommand(
                    item.getDomain(), item.getTitle(), item.getBody(), item.getSourceUrl(), item.getCodeValues())
                    .orElse(null);
            if (cmd == null) {
                log.warn("[ETL] 필수 필드 누락으로 skip: {}", item);
                return null; // null → 해당 아이템 필터링(skip)
            }
            return KnowledgeRecord.forIngestion(
                    cmd.domain(), cmd.title(), cmd.body(), cmd.sourceUrl(),
                    cmd.codeValues(), cmd.sourceUpdatedAt(), cmd.contentHash());
        };
    }

    /**
     * insert-or-skip 라이터: content_hash 중복(이미 존재 또는 같은 청크 내 중복)이면 skip.
     */
    @Bean
    public ItemWriter<KnowledgeRecord> settlementWriter(KnowledgeRecordRepository repository) {
        return items -> {
            java.util.Set<String> seenInChunk = new java.util.HashSet<>();
            for (KnowledgeRecord record : items) {
                String hash = record.getContentHash().value();
                if (!seenInChunk.add(hash)) {
                    log.info("[ETL] 청크 내 중복 skip: hash={}", hash);
                    continue;
                }
                if (repository.existsByContentHash(hash)) {
                    log.info("[ETL] 기존 레코드 중복 skip: hash={}", hash);
                    continue;
                }
                repository.save(record);
            }
        };
    }
}
