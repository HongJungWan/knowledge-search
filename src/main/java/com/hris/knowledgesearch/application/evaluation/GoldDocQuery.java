package com.hris.knowledgesearch.application.evaluation;

import java.util.List;
import java.util.Map;

/**
 * 하이브리드 검색 평가 정답셋 한 건.
 * <p>
 * {@code evaluation/gold_search.csv} 의 한 행 — 질의, 질의 유형(정형/비정형), 그 질의가 찾아야 하는 문서를
 * 식별하는 제목 부분문자열 목록, 정형 질의의 코드값 필터(선택).
 * 반환 레코드의 {@code title} 이 {@link #expectedTitleContains} 중 하나라도 포함하면 관련(relevant)으로 본다 —
 * 시드/ETL 의 자동 증분 id 에 의존하지 않아 데이터 변경에 강하다.
 */
public record GoldDocQuery(
        String queryId,
        String query,
        Kind kind,
        List<String> expectedTitleContains,
        Map<String, String> codeValues) {

    /** 질의 유형 — 운영 관찰 50/50 분포의 두 계층. */
    public enum Kind {
        /** 코드값·식별자·정확 용어 중심(키워드가 이미 강함). */
        STRUCTURED,
        /** 자연어 패러프레이즈(키워드 약함 → 벡터 기여 기대). */
        UNSTRUCTURED
    }

    /** 반환 레코드가 이 정답에 관련되는지(제목 부분문자열 포함). */
    public boolean isRelevant(String title) {
        if (title == null) {
            return false;
        }
        return expectedTitleContains.stream().anyMatch(title::contains);
    }
}
