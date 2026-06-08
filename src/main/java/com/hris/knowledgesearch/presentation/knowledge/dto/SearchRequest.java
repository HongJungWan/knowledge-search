package com.hris.knowledgesearch.presentation.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 지식 검색 요청 (search_knowledge 도구 / REST).
 */
@Schema(description = "지식 검색 요청")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchRequest {

    @Schema(description = "검색 질의 (자연어)", example = "미정산 가맹점 지난달")
    @NotBlank
    private String query;

    @Schema(description = "도메인 필터 (선택)", example = "SETTLEMENT")
    private String domain;

    @Schema(description = "코드값 필터 (선택) 예: {\"settlement_status\":\"PENDING\"}")
    private Map<String, String> filters;

    @Schema(description = "최대 반환 건수 (기본 10)", example = "10")
    private Integer limit;

    public SearchRequest(String query, String domain, Map<String, String> filters, Integer limit) {
        this.query = query;
        this.domain = domain;
        this.filters = filters;
        this.limit = limit;
    }
}
