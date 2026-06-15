package com.hris.knowledgesearch.application.knowledge.port;

/**
 * metadata 물리 스키마 카탈로그 조회 포트 (아웃바운드 ACL).
 * <p>
 * 물리 스키마의 SSOT 는 metadata-ontology({@code SchemaCatalog})다. {@code list_schema} 가
 * 하드코딩 카탈로그 대신 이 포트로 카탈로그를 받아온다. 구현(어댑터)은 infrastructure 에 두며
 * ({@code infrastructure.metadata.MetadataSchemaClient}), 비활성/실패는 폴백으로 흡수한다.
 * {@link MetadataResolvePort} 와 동일한 ACL 패턴.
 */
public interface SchemaCatalogPort {

    /**
     * 도메인의 물리 스키마 카탈로그를 조회한다.
     *
     * @param domain 도메인 필터 (현재 metadata 카탈로그는 단일 도메인이라 통과; 호출자 응답 표기에 사용)
     * @return 번역된 카탈로그. 비활성/실패 시 {@link SchemaCatalogResult#unavailable()}.
     */
    SchemaCatalogResult listSchema(String domain);
}
