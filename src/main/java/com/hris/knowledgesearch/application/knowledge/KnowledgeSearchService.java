package com.hris.knowledgesearch.application.knowledge;

import com.hris.knowledgesearch.application.knowledge.port.MetadataResolvePort;
import com.hris.knowledgesearch.application.knowledge.port.MetadataResolveResult;
import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecord;
import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecordRepository;
import com.hris.knowledgesearch.domain.knowledge.SearchLog;
import com.hris.knowledgesearch.domain.knowledge.SearchLogRepository;
import com.hris.knowledgesearch.global.exception.BusinessException;
import com.hris.knowledgesearch.global.exception.ErrorCode;
import com.hris.knowledgesearch.application.knowledge.dto.KnowledgeDetailResponse;
import com.hris.knowledgesearch.application.knowledge.dto.KnowledgeSummaryResponse;
import com.hris.knowledgesearch.application.knowledge.dto.SchemaInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 지식 검색 서비스 (PRD §5/§6/§9). 응용 서비스 — 흐름 제어만, 도메인 포트에 의존.
 * <p>
 * 흐름: metadata 로 질의 정규화·매핑(활성 시) → 도메인 포트 조건 검색 → 요약/출처 반환 → SearchLog 기록.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeSearchService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final KnowledgeRecordRepository knowledgeRecordRepository;
    private final SearchLogRepository searchLogRepository;
    private final MetadataResolvePort metadataPort;

    /**
     * 지식 검색 (search_knowledge).
     *
     * @param query   자연어 질의
     * @param domain  도메인 필터 (null 허용)
     * @param filters 코드값 필터 (null 허용)
     * @param limit   최대 건수 (null 이면 기본 10)
     */
    @Transactional
    public List<KnowledgeSummaryResponse> search(String query, String domain,
                                                 Map<String, String> filters, Integer limit) {
        if (!StringUtils.hasText(query)) {
            throw new BusinessException(ErrorCode.INVALID_QUERY);
        }
        long start = System.currentTimeMillis();
        int effectiveLimit = clampLimit(limit);

        // 1) metadata 로 정규화·매핑 (비활성 시 원본 질의 폴백)
        MetadataResolveResult resolved = metadataPort.resolve(query);
        String normalized = resolved.normalizedQuery();

        // metadata 가 코드값 매핑을 준 경우 filters 에 보강 (호출자 filters 우선)
        Map<String, String> codeValues = mergeCodeValues(resolved, filters);

        // 2) 도메인 포트 검색
        List<KnowledgeRecord> records = knowledgeRecordRepository.search(domain, normalized, codeValues, effectiveLimit);

        // 3) 요약 변환
        List<KnowledgeSummaryResponse> summaries = records.stream()
                .map(KnowledgeSummaryResponse::from)
                .toList();

        // 4) 로그 기록 (PRD §9)
        writeLog(query, normalized, "search_knowledge", start, summaries.size());
        return summaries;
    }

    /**
     * 단건 상세 조회 (get_record).
     */
    @Transactional
    public KnowledgeDetailResponse getRecord(Long id) {
        long start = System.currentTimeMillis();
        KnowledgeRecord record = knowledgeRecordRepository.findById(id)
                .filter(r -> !r.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        writeLog(String.valueOf(id), null, "get_record", start, 1);
        return KnowledgeDetailResponse.from(record);
    }

    /**
     * 검색 가능 스키마 설명 (list_schema).
     * <p>
     * 현재는 정적 카탈로그를 제공한다. 운영에서는 metadata 카탈로그를 사용한다(PRD §5.1).
     */
    @Transactional(readOnly = true)
    public SchemaInfoResponse listSchema(String domain) {
        String effectiveDomain = StringUtils.hasText(domain) ? domain : "SETTLEMENT";
        return SchemaInfoResponse.builder()
                .domain(effectiveDomain)
                .source("static-catalog")
                .column(SchemaInfoResponse.ColumnInfo.builder()
                        .table("knowledge_record").column("domain")
                        .description("지식 도메인 분류").exampleCodeValues("SETTLEMENT").build())
                .column(SchemaInfoResponse.ColumnInfo.builder()
                        .table("knowledge_record").column("title")
                        .description("제목 (키워드 검색 대상)").build())
                .column(SchemaInfoResponse.ColumnInfo.builder()
                        .table("knowledge_record").column("body")
                        .description("본문 (키워드 검색 대상)").build())
                .column(SchemaInfoResponse.ColumnInfo.builder()
                        .table("knowledge_record").column("code_values")
                        .description("코드값 묶음 (운영 Redshift SUPER). 코드값 일치 검색 대상")
                        .exampleCodeValues("{\"settlement_status\":\"PENDING|HOLD|DONE\"}").build())
                .column(SchemaInfoResponse.ColumnInfo.builder()
                        .table("knowledge_record").column("source_url")
                        .description("출처 링크/식별자 (응답에 항상 포함)").build())
                .build();
    }

    private Map<String, String> mergeCodeValues(MetadataResolveResult resolved, Map<String, String> filters) {
        java.util.Map<String, String> merged = new java.util.HashMap<>();
        if (resolved.columnMappings() != null) {
            resolved.columnMappings().stream()
                    .filter(m -> StringUtils.hasText(m.physicalColumn()) && StringUtils.hasText(m.codeValue()))
                    .forEach(m -> merged.put(m.physicalColumn(), m.codeValue()));
        }
        if (filters != null) {
            merged.putAll(filters); // 호출자 명시 필터가 우선
        }
        return merged;
    }

    private void writeLog(String raw, String normalized, String tool, long start, int hitCount) {
        try {
            searchLogRepository.save(SearchLog.builder()
                    .queryRaw(raw)
                    .queryNormalized(normalized)
                    .tool(tool)
                    .latencyMs(System.currentTimeMillis() - start)
                    .hitCount(hitCount)
                    .build());
        } catch (Exception e) {
            // 로깅 실패가 검색 자체를 막지 않게 한다.
            log.warn("SearchLog 기록 실패: {}", e.getMessage());
        }
    }

    private int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
