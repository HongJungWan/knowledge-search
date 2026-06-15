package com.hris.knowledgesearch.infrastructure.config;

import com.hris.knowledgesearch.domain.knowledge.QueryRouter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 도메인 서비스 빈 등록.
 * <p>
 * 도메인 서비스({@code @DomainService})는 Spring 스테레오타입을 갖지 않으므로(도메인 순수성)
 * 여기서 명시적으로 빈으로 등록한다.
 */
@Configuration
public class DomainServiceConfig {

    @Bean
    public QueryRouter queryRouter() {
        return new QueryRouter();
    }
}
