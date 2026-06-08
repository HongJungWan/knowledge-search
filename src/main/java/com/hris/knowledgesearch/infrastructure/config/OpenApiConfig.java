package com.hris.knowledgesearch.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 설정.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI knowledgeSearchOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Knowledge Search API")
                        .description("사내 정형 지식 검색(RAG) 백엔드 + MCP 서버 (정산 도메인)")
                        .version("v1.1.0"));
    }
}
