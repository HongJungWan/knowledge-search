package com.hris.knowledgesearch.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine 기반 캐시 설정.
 * <p>
 * metadata 서비스의 {@code /api/resolve} 결과를 캐싱한다 (PRD §6, 캐싱은 소비자 측 책임).
 * 같은 질의가 반복될 때 metadata 왕복을 줄여 도구 호출당 평균 응답 1초 목표(PRD §1.4)에 기여한다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** metadata /resolve 결과 캐시 이름 */
    public static final String METADATA_RESOLVE_CACHE = "metadataResolve";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(METADATA_RESOLVE_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(10, TimeUnit.MINUTES));
        return manager;
    }
}
