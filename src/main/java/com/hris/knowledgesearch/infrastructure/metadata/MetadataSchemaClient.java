package com.hris.knowledgesearch.infrastructure.metadata;

import com.hris.knowledgesearch.application.knowledge.port.SchemaCatalogPort;
import com.hris.knowledgesearch.application.knowledge.port.SchemaCatalogResult;
import com.hris.knowledgesearch.infrastructure.config.CacheConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * metadata-ontology 스키마 카탈로그 클라이언트 (list_schema). 아웃바운드 어댑터.
 * <p>
 * // ACL: metadata 바운디드 컨텍스트에 대한 anti-corruption 어댑터다. 외부 응답({@code SchemaCatalogResponse})을
 * 우리 쪽 {@link SchemaCatalogResult} 로 번역하고, 비활성/실패를 {@link SchemaCatalogResult#unavailable()}
 * 폴백으로 흡수해 외부 모델/장애가 전파되지 않게 한다. {@link MetadataClient} 와 동일 패턴.
 * <p>
 * 기능은 플래그 {@code metadata.enabled}(기본 false) 뒤에 둔다. 비활성/실패 시 소비자(서비스)는
 * 기존 정적 카탈로그로 폴백한다. 결과는 Caffeine 캐시({@value CacheConfig#METADATA_SCHEMA_CACHE})에 담는다.
 */
@Slf4j
@Component
public class MetadataSchemaClient implements SchemaCatalogPort {

    private final boolean enabled;
    private final RestClient restClient;

    public MetadataSchemaClient(
            @Value("${metadata.enabled:false}") boolean enabled,
            @Value("${metadata.base-url:http://localhost:8096}") String baseUrl) {
        this.enabled = enabled;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * metadata 카탈로그를 조회한다.
     * <p>
     * 플래그가 꺼져 있으면 호출하지 않고 {@code unavailable()} 폴백을 반환한다. 호출 실패도 폴백으로
     * 흡수한다(list_schema 는 metadata 없이도 동작해야 한다). 폴백도 캐시 TTL 동안 유지된다.
     * <p>
     * metadata 카탈로그는 현재 단일 도메인이라 도메인 필터는 전체 통과시킨다(호출자 응답 표기에만 사용).
     */
    @Cacheable(cacheNames = CacheConfig.METADATA_SCHEMA_CACHE, key = "#domain")
    public SchemaCatalogResult listSchema(String domain) {
        if (!enabled) {
            return SchemaCatalogResult.unavailable();
        }
        try {
            List<CatalogEntry> entries = restClient.get()
                    .uri("/api/admin/schema/catalogs")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<CatalogEntry>>() {});
            if (entries == null || entries.isEmpty()) {
                return SchemaCatalogResult.unavailable();
            }
            List<SchemaCatalogResult.Column> columns = entries.stream()
                    .map(e -> new SchemaCatalogResult.Column(
                            e.physicalTable(), e.physicalColumn(), e.dataType(), e.description(), e.sourceSystem()))
                    .toList();
            return SchemaCatalogResult.of(columns);
        } catch (Exception e) {
            log.warn("metadata /api/admin/schema/catalogs 호출 실패 — 정적 카탈로그로 폴백합니다. err={}",
                    e.getMessage());
            return SchemaCatalogResult.unavailable();
        }
    }

    /** metadata-ontology 응답 역직렬화 전용 로컬 모델(경계 안에만 존재). */
    private record CatalogEntry(String physicalTable, String physicalColumn, String dataType,
                                String description, String sourceSystem) {
    }
}
