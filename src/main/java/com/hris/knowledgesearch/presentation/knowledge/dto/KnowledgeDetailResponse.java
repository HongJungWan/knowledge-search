package com.hris.knowledgesearch.presentation.knowledge.dto;

import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 지식 레코드 단건 상세 응답 (get_record 결과).
 * <p>
 * 원문 전체와 메타데이터·출처를 함께 준다(PRD §5.1).
 */
@Schema(description = "지식 레코드 상세")
@Getter
@Builder
public class KnowledgeDetailResponse {

    @Schema(description = "레코드 ID", example = "1")
    private Long id;

    @Schema(description = "도메인", example = "SETTLEMENT")
    private String domain;

    @Schema(description = "제목")
    private String title;

    @Schema(description = "본문 원문")
    private String body;

    @Schema(description = "출처 링크/식별자")
    private String sourceUrl;

    @Schema(description = "코드값 (JSON)")
    private String codeValues;

    @Schema(description = "콘텐츠 해시 (SHA-256)")
    private String contentHash;

    @Schema(description = "소스 기준 최종 수정 시각")
    private Instant sourceUpdatedAt;

    public static KnowledgeDetailResponse from(KnowledgeRecord r) {
        return KnowledgeDetailResponse.builder()
                .id(r.getId())
                .domain(r.getDomain())
                .title(r.getTitle())
                .body(r.getBody())
                .sourceUrl(r.getSourceUrl())
                .codeValues(r.getCodeValues())
                .contentHash(r.getContentHash())
                .sourceUpdatedAt(r.getSourceUpdatedAt())
                .build();
    }
}
