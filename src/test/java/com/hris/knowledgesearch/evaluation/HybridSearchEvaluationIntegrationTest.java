package com.hris.knowledgesearch.evaluation;

import com.hris.knowledgesearch.application.evaluation.HybridEvaluationService;
import com.hris.knowledgesearch.application.evaluation.dto.response.HybridEvaluationReportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 하이브리드 검색 실효성 측정(옵트인) — 도커 pgvector + Ollama(bge-m3) 필요.
 * <p>
 * 사전 준비:
 * <ol>
 *   <li>{@code docker compose -f docker/docker-compose.postgres.yml up -d}</li>
 *   <li>{@code ollama pull bge-m3 && ollama serve}</li>
 *   <li>{@code RUN_SEMANTIC_EVAL=true ./gradlew test --tests '*HybridSearchEvaluationIntegrationTest'}</li>
 * </ol>
 * 환경변수 {@code RUN_SEMANTIC_EVAL=true} 가 없으면 비활성(기본 CI 게이트에서 제외 — 결정론 스텁 수치로
 * 게이팅하지 않는다는 원칙). ETL 적재는 Postgres 어댑터 save() 가 적재 시 임베딩을 함께 기록하므로 별도
 * 백필 없이 평가 가능하다.
 */
@SpringBootTest
@ActiveProfiles({"postgres", "localmodel"})
@Tag("semantic")
@EnabledIfEnvironmentVariable(named = "RUN_SEMANTIC_EVAL", matches = "true")
class HybridSearchEvaluationIntegrationTest {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job settlementIngestionJob;

    @Autowired
    private HybridEvaluationService hybridEvaluationService;

    @BeforeEach
    void loadData() throws Exception {
        // ETL 적재(멱등 — content_hash 중복 skip). Postgres 어댑터 save() 가 임베딩을 함께 기록한다.
        jobLauncher.run(settlementIngestionJob, new JobParametersBuilder()
                .addLong("requestedAt", System.currentTimeMillis())
                .toJobParameters());
    }

    @Test
    @DisplayName("하이브리드는 비정형 재현율을 키우고 정형을 회귀시키지 않는다(채택 게이트)")
    void hybridBeatsKeywordOnUnstructuredWithoutStructuredRegression() {
        HybridEvaluationReportResponse report = hybridEvaluationService.evaluate(3, 10);

        System.out.println("[HYBRID EVAL] " + report.gate().summary());
        System.out.println("  KEYWORD overall recall@3=" + report.keyword().overall().recallAtK()
                + " / HYBRID overall recall@3=" + report.hybrid().overall().recallAtK());

        assertThat(report.gate().structuredNotRegressed())
                .as("정형 계층 무회귀 — 하이브리드가 정밀도를 깨면 안 됨")
                .isTrue();
        assertThat(report.gate().unstructuredRecallImproved())
                .as("비정형 계층 재현율 향상 — 50/50 에서 벡터의 실효성")
                .isTrue();
    }
}
