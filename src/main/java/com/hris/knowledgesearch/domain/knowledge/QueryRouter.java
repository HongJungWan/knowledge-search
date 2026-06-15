package com.hris.knowledgesearch.domain.knowledge;

import com.hris.knowledgesearch.shared.ddd.DomainService;

/**
 * 질의 라우팅 도메인 서비스 (로드맵 #2).
 * <p>
 * 질의에 <b>정형 신호</b>(짧은 키워드구·숫자·코드형 토큰)가 있는지 판정해 metadata-ontology(MO)
 * 경유 여부를 결정한다. 긴 자연어 문장은 평가상 MO 코드 매핑 기여가 없으므로(META=KEYWORD) MO 왕복을
 * 생략해 지연·결합을 줄인다. 정밀도 보존을 위해 <b>의심 시 MO 경유</b> 쪽으로 보수적으로 편향한다.
 * <p>
 * 도메인이 소유하는 순수 로직이다(Spring/시계/HTTP 타입 없음). 빈 등록은
 * {@code infrastructure.config.DomainServiceConfig} 의 {@code @Bean}.
 */
@DomainService
public class QueryRouter {

    /** 이 토큰 수 이하의 짧은 질의는 키워드/코드형으로 보고 MO 경유시킨다. */
    private static final int SHORT_QUERY_TOKEN_THRESHOLD = 4;

    /**
     * 이 질의를 metadata-ontology 로 해석할지 여부.
     * <p>
     * {@code true}(MO 경유): 토큰 수 ≤ {@value #SHORT_QUERY_TOKEN_THRESHOLD} <b>또는</b> 숫자 포함
     * <b>또는</b> 코드형 토큰(ASCII 대문자 2자 이상, 예: {@code PENDING}) 포함.
     * 그 외(긴 자연어·정형 신호 없음) → {@code false}(MO 생략, 벡터 단독).
     *
     * @param query 자연어 질의 (null/blank 면 보수적으로 true)
     */
    public boolean shouldResolveViaMetadata(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String[] tokens = query.trim().split("\\s+");
        if (tokens.length <= SHORT_QUERY_TOKEN_THRESHOLD) {
            return true;
        }
        for (String token : tokens) {
            if (hasDigit(token) || isCodeLike(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDigit(String token) {
        for (int i = 0; i < token.length(); i++) {
            if (Character.isDigit(token.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** ASCII 대문자 2자 이상 연속 토큰(코드값/식별자 신호, 예: PENDING, T+2 의 T 는 1자라 제외). */
    private boolean isCodeLike(String token) {
        int upperRun = 0;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                upperRun++;
                if (upperRun >= 2) {
                    return true;
                }
            } else {
                upperRun = 0;
            }
        }
        return false;
    }
}
