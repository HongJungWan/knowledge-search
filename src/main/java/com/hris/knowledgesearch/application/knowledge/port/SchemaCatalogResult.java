package com.hris.knowledgesearch.application.knowledge.port;

import java.util.List;

/**
 * metadata 물리 스키마 카탈로그 조회 결과 (번역된 ACL 산출물).
 * <p>
 * metadata-ontology 의 응답 모델을 경계 밖으로 누출하지 않도록 우리 쪽 형태로 번역한 값이다.
 * 비활성/실패 시 {@link #unavailable()} 를 반환하며, 이 경우 소비자(서비스)는 정적 카탈로그로 폴백한다.
 *
 * @param available metadata 카탈로그를 실제로 받아왔는지 여부 (false 면 columns 는 비어 있고 폴백 대상)
 * @param columns   번역된 컬럼 목록
 */
public record SchemaCatalogResult(boolean available, List<Column> columns) {

    /** 번역된 카탈로그 컬럼 한 건. */
    public record Column(String table, String column, String dataType, String description, String sourceSystem) {
    }

    /** metadata 카탈로그를 받아온 경우. */
    public static SchemaCatalogResult of(List<Column> columns) {
        return new SchemaCatalogResult(true, columns == null ? List.of() : columns);
    }

    /** metadata 비활성/실패 — 소비자는 정적 카탈로그로 폴백한다. */
    public static SchemaCatalogResult unavailable() {
        return new SchemaCatalogResult(false, List.of());
    }
}
