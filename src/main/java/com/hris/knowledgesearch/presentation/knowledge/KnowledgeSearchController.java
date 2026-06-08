package com.hris.knowledgesearch.presentation.knowledge;

import com.hris.knowledgesearch.application.knowledge.KnowledgeSearchService;
import com.hris.knowledgesearch.global.common.ApiResponse;
import com.hris.knowledgesearch.application.knowledge.dto.KnowledgeDetailResponse;
import com.hris.knowledgesearch.application.knowledge.dto.KnowledgeSummaryResponse;
import com.hris.knowledgesearch.application.knowledge.dto.SchemaInfoResponse;
import com.hris.knowledgesearch.application.knowledge.dto.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 지식 검색 REST API (PRD §5).
 * <p>
 * MCP 도구와 동일한 기능을 REST 로도 노출해, MCP 없이도 검증/디버깅할 수 있게 한다.
 */
@Tag(name = "Knowledge Search", description = "사내 정형 지식 검색 API (정산 도메인)")
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeSearchController {

    private final KnowledgeSearchService knowledgeSearchService;

    @Operation(summary = "지식 검색", description = "자연어 질의로 정형 지식을 검색한다 (요약 + 출처).")
    @PostMapping("/search")
    public ResponseEntity<ApiResponse<List<KnowledgeSummaryResponse>>> search(
            @Valid @RequestBody SearchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                knowledgeSearchService.search(
                        request.getQuery(), request.getDomain(), request.getFilters(), request.getLimit())));
    }

    @Operation(summary = "지식 단건 조회", description = "ID 로 지식 레코드 원문 전체를 조회한다.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<KnowledgeDetailResponse>> getRecord(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(knowledgeSearchService.getRecord(id)));
    }

    @Operation(summary = "스키마 설명", description = "검색 가능한 테이블·컬럼·코드값 설명을 반환한다.")
    @GetMapping("/schema")
    public ResponseEntity<ApiResponse<SchemaInfoResponse>> listSchema(
            @RequestParam(required = false) String domain) {
        return ResponseEntity.ok(ApiResponse.success(knowledgeSearchService.listSchema(domain)));
    }
}
