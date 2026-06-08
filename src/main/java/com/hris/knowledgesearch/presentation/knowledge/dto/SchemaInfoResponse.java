package com.hris.knowledgesearch.presentation.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * 검색 가능한 스키마 설명 응답 (list_schema 결과).
 * <p>
 * 어떤 테이블·컬럼·코드값으로 검색되는지 LLM 에 먼저 깔아준다(PRD §5.1, §8).
 * 운영에서는 metadata 카탈로그를 사용하지만, 현재는 로컬 정적 설명을 제공한다.
 */
@Schema(description = "검색 가능 스키마 설명")
@Getter
@Builder
public class SchemaInfoResponse {

    @Schema(description = "도메인", example = "SETTLEMENT")
    private String domain;

    @Schema(description = "검색 대상 테이블·컬럼·코드값 설명")
    @Singular
    private List<ColumnInfo> columns;

    @Schema(description = "메타데이터 출처 (정적 카탈로그 / metadata 서비스)")
    private String source;

    @Schema(description = "스키마 컬럼 설명")
    @Getter
    @Builder
    public static class ColumnInfo {

        @Schema(description = "물리 테이블", example = "knowledge_record")
        private String table;

        @Schema(description = "물리 컬럼", example = "domain")
        private String column;

        @Schema(description = "설명")
        private String description;

        @Schema(description = "예시 코드값 (있으면)")
        private String exampleCodeValues;
    }
}
