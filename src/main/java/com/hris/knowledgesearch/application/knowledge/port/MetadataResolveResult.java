package com.hris.knowledgesearch.application.knowledge.port;

import java.util.List;

/**
 * metadata 서비스 {@code POST /api/resolve} 응답 매핑 DTO (포트 반환 타입).
 * <p>
 * metadata-ontology 의 {@code ResolveResponse} 형식을 그대로 받는다
 * ({@code {normalizedQuery, terms[], columnMappings[], timeRange, unmapped[]}}).
 * 비활성/실패 시 {@link #raw(String)} 폴백을 쓴다.
 */
public record MetadataResolveResult(
        String normalizedQuery,
        List<ResolvedTerm> terms,
        List<ColumnMapping> columnMappings,
        TimeRange timeRange,
        List<String> unmapped
) {

    public record ResolvedTerm(String canonical, String matchedSurface) {
    }

    public record ColumnMapping(String physicalTable, String physicalColumn, String codeValue) {
    }

    public record TimeRange(String from, String to) {
    }

    /** metadata 미사용/실패 시 원본 질의를 그대로 정규화 질의로 쓰는 no-op 폴백. */
    public static MetadataResolveResult raw(String query) {
        return new MetadataResolveResult(query, List.of(), List.of(), null, List.of());
    }
}
