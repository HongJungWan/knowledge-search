package com.hris.knowledgesearch.application.knowledge.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * 지식 검색 커맨드 (search_knowledge 도구 / REST 입력).
 * <p>
 * 응용 계층의 의도(intent)를 표현하는 커맨드 객체다. REST 요청 본문을 그대로 받으므로
 * 필드명/JSON 형태는 기존 요청 계약과 동일하다(query, domain, filters, limit).
 */
@Schema(description = "지식 검색 요청")
public record SearchKnowledgeCommand(

        @Schema(description = "검색 질의 (자연어)", example = "미정산 가맹점 지난달")
        @NotBlank
        String query,

        @Schema(description = "도메인 필터 (선택)", example = "SETTLEMENT")
        String domain,

        @Schema(description = "코드값 필터 (선택) 예: {\"settlement_status\":\"PENDING\"}")
        Map<String, String> filters,

        @Schema(description = "최대 반환 건수 (기본 10)", example = "10")
        Integer limit
) {
    // 입력 검증은 @NotBlank + 컨트롤러의 @Valid 로 처리한다(기존 SearchRequest 와 동일한 400 응답 보존).
    // compact 생성자에서 throw 해도 GlobalExceptionHandler 가 HttpMessageNotReadableException 을 400 으로 매핑한다.
}
