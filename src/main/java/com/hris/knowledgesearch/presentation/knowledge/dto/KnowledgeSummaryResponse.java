package com.hris.knowledgesearch.presentation.knowledge.dto;

import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 지식 레코드 요약 응답 (search_knowledge 결과 항목).
 * <p>
 * 요약을 먼저 주고 원문은 get_record 로 분리해 토큰을 줄인다(PRD §5.3). 출처를 항상 포함한다.
 */
@Schema(description = "지식 레코드 요약")
@Getter
@Builder
public class KnowledgeSummaryResponse {

    @Schema(description = "레코드 ID", example = "1")
    private Long id;

    @Schema(description = "도메인", example = "SETTLEMENT")
    private String domain;

    @Schema(description = "제목", example = "정산 마감 정책")
    private String title;

    @Schema(description = "본문 발췌 (앞 200자)")
    private String snippet;

    @Schema(description = "출처 링크/식별자")
    private String sourceUrl;

    @Schema(description = "코드값 (JSON)", example = "{\"settlement_status\":\"PENDING\"}")
    private String codeValues;

    @Schema(description = "소스 기준 최종 수정 시각")
    private Instant sourceUpdatedAt;

    /** 본문 발췌 길이 */
    private static final int SNIPPET_LEN = 200;

    public static KnowledgeSummaryResponse from(KnowledgeRecord r) {
        String body = r.getBody() == null ? "" : r.getBody();
        String snippet = body.length() > SNIPPET_LEN ? body.substring(0, SNIPPET_LEN) + "…" : body;
        return KnowledgeSummaryResponse.builder()
                .id(r.getId())
                .domain(r.getDomain())
                .title(r.getTitle())
                .snippet(snippet)
                .sourceUrl(r.getSourceUrl())
                .codeValues(r.getCodeValues())
                .sourceUpdatedAt(r.getSourceUpdatedAt())
                .build();
    }
}
