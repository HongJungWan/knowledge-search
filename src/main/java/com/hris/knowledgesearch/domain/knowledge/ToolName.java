package com.hris.knowledgesearch.domain.knowledge;

import com.hris.knowledgesearch.shared.ddd.ValueObject;

/**
 * 호출된 MCP 도구 종류 (PRD §9 관측성).
 * <p>
 * {@link SearchLog#tool} 의 타입. 질의 필터 대상이 아니라 로그 분류용 값이므로 enum 으로 안전하게 고정한다
 * (검색 SQL 의 query target 이 아님). DB 에는 {@code @Enumerated(STRING)} 으로 이름을 저장한다.
 */
@ValueObject
public enum ToolName {
    SEARCH_KNOWLEDGE,
    GET_RECORD,
    /** 현재 기록 경로 없음(향후 listSchema 로깅용) */
    LIST_SCHEMA
}
