package com.hris.knowledgesearch.infrastructure.config;

import com.hris.knowledgesearch.presentation.mcp.KnowledgeSearchTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 서버 설정 (PRD §5).
 * <p>
 * {@link KnowledgeSearchTools} 의 {@code @Tool} 메서드를 MCP 도구 콜백으로 등록한다.
 * Spring AI MCP Server(webmvc) 스타터가 이 ToolCallbackProvider 를 자동으로 노출한다.
 *
 * TODO: verify Spring AI version (PRD §12) — MethodToolCallbackProvider 빌더 API 는
 *       spring-ai 1.0.0 (org.springframework.ai.tool.method) 기준. 버전 업 시 확인한다.
 */
@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider knowledgeSearchToolCallbackProvider(KnowledgeSearchTools tools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build();
    }
}
