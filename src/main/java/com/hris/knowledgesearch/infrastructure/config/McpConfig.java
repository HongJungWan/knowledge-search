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
 * Spring AI MCP Server(webmvc, 1.0.0) 스타터가 이 ToolCallbackProvider 를 SSE 전송
 * ({@code /sse} + {@code /mcp/message})으로 노출한다 — Claude Code 연결은 레포 루트 {@code .mcp.json}.
 * 1.1.x 업그레이드 시 MethodToolCallbackProvider API 변동과 streamable HTTP 전환을 함께 본다.
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
