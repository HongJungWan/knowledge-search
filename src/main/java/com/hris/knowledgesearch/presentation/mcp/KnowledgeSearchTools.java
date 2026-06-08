package com.hris.knowledgesearch.presentation.mcp;

import com.hris.knowledgesearch.application.knowledge.KnowledgeSearchService;
import com.hris.knowledgesearch.application.knowledge.dto.KnowledgeDetailResponse;
import com.hris.knowledgesearch.application.knowledge.dto.KnowledgeSummaryResponse;
import com.hris.knowledgesearch.application.knowledge.dto.SchemaInfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MCP 도구 정의 (PRD §5). 인바운드 어댑터.
 * <p>
 * Spring AI 의 {@link Tool} 어노테이션으로 도구를 노출하고, {@link KnowledgeSearchService} 에 위임한다.
 * <p>
 * 도구 호출 정책(PRD §5.2):
 * <ol>
 *   <li>검색은 {@code search_knowledge} 로 시작한다.</li>
 *   <li>간단한 질의는 요약 결과만으로 답한다.</li>
 *   <li>근거가 더 필요하면 {@code get_record} 로 원문을 가져온다.</li>
 *   <li>결과가 부족하거나 모호하면 질의를 다듬어 다시 검색한다(최대 N회).</li>
 *   <li>어떤 필드가 있는지 모르면 {@code list_schema} 를 먼저 부른다.</li>
 * </ol>
 *
 * TODO: verify Spring AI version (PRD §12) — @Tool/@ToolParam 및 MethodToolCallbackProvider 는
 *       spring-ai 1.0.0 (org.springframework.ai.tool.*) 기준이다. 버전 업그레이드 시 시그니처 확인.
 */
@Component
@RequiredArgsConstructor
public class KnowledgeSearchTools {

    private final KnowledgeSearchService knowledgeSearchService;

    @Tool(name = "search_knowledge",
            description = "사내 정형 지식(정산 도메인)을 자연어 질의로 검색해 요약 목록과 출처를 돌려준다. "
                    + "검색은 이 도구로 시작한다. 간단한 질의는 요약만으로 답하고, 근거가 더 필요하면 get_record 를 쓴다.")
    public List<KnowledgeSummaryResponse> searchKnowledge(
            @ToolParam(description = "검색 질의 (자연어). 예: '미정산 가맹점 지난달'") String query,
            @ToolParam(required = false, description = "도메인 필터. 예: SETTLEMENT") String domain,
            @ToolParam(required = false, description = "코드값 필터. 예: {\"settlement_status\":\"PENDING\"}")
            Map<String, String> filters,
            @ToolParam(required = false, description = "최대 반환 건수 (기본 10)") Integer limit) {
        return knowledgeSearchService.search(query, domain, filters, limit);
    }

    @Tool(name = "get_record",
            description = "지식 레코드 ID 로 원문 전체와 메타데이터·출처를 가져온다. 근거 보강용 단건 조회.")
    public KnowledgeDetailResponse getRecord(
            @ToolParam(description = "지식 레코드 ID") Long id) {
        return knowledgeSearchService.getRecord(id);
    }

    @Tool(name = "list_schema",
            description = "검색 가능한 테이블·컬럼·코드값 설명을 돌려준다. 어떤 필드로 검색되는지 모르면 먼저 호출한다.")
    public SchemaInfoResponse listSchema(
            @ToolParam(required = false, description = "도메인. 예: SETTLEMENT") String domain) {
        return knowledgeSearchService.listSchema(domain);
    }
}
