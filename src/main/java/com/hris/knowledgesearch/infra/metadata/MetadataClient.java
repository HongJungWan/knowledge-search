package com.hris.knowledgesearch.infra.metadata;

import com.hris.knowledgesearch.application.knowledge.port.MetadataResolvePort;
import com.hris.knowledgesearch.application.knowledge.port.MetadataResolveResult;
import com.hris.knowledgesearch.global.config.CacheConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * metadata-ontology 서비스 클라이언트 (PRD §2/§6).
 * <p>
 * 자연어 질의를 표준 용어·물리 컬럼·코드값·기간으로 해석받는다. 기능은 플래그 {@code metadata.enabled}
 * (기본 false) 뒤에 둔다. 비활성이거나 호출이 실패하면 원본 질의를 그대로 쓰는 no-op 폴백을 반환한다.
 * 결과는 Caffeine 캐시({@value CacheConfig#METADATA_RESOLVE_CACHE})에 담는다.
 */
@Slf4j
@Component
public class MetadataClient implements MetadataResolvePort {

    private final boolean enabled;
    private final RestClient restClient;

    public MetadataClient(
            @Value("${metadata.enabled:false}") boolean enabled,
            // TODO(AWS): 운영 배포 시 metadata 서비스 주소를 환경변수(METADATA_BASE_URL)로 주입한다.
            @Value("${metadata.base-url:http://localhost:8096}") String baseUrl) {
        this.enabled = enabled;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * 질의를 metadata 서비스로 해석한다.
     * <p>
     * 플래그가 꺼져 있으면 호출하지 않고 원본 질의 폴백을 반환한다. 호출 실패도 폴백으로 흡수한다
     * (검색은 metadata 없이도 동작해야 한다).
     */
    @Cacheable(cacheNames = CacheConfig.METADATA_RESOLVE_CACHE, key = "#query", unless = "#result == null")
    public MetadataResolveResult resolve(String query) {
        if (!enabled) {
            return MetadataResolveResult.raw(query);
        }
        try {
            MetadataResolveResult result = restClient.post()
                    .uri("/api/resolve")
                    .body(Map.of("query", query))
                    .retrieve()
                    .body(MetadataResolveResult.class);
            return result != null ? result : MetadataResolveResult.raw(query);
        } catch (Exception e) {
            log.warn("metadata /api/resolve 호출 실패 — 원본 질의로 폴백합니다. query='{}', err={}",
                    query, e.getMessage());
            return MetadataResolveResult.raw(query);
        }
    }
}
