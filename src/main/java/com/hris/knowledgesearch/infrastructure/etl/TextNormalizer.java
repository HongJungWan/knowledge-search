package com.hris.knowledgesearch.infrastructure.etl;

import java.text.Normalizer;

/**
 * 문자열 정규화 유틸 (PRD §7).
 * <p>
 * 적재 단계에서 공백·대소문자·전각/반각·표기를 통일해 매칭이 더 잘 걸리게 한다.
 * 순수 함수로 두어 테스트 가능하게 한다.
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    /**
     * 표준 정규화:
     * <ol>
     *   <li>유니코드 NFKC 정규화 (전각→반각, 호환 문자 통일)</li>
     *   <li>앞뒤 공백 제거(trim)</li>
     *   <li>연속 공백·탭·개행을 단일 공백으로 축약</li>
     * </ol>
     * 대소문자는 본문 의미 보존을 위해 그대로 둔다(검색은 title 만 ignoreCase, body 는 대소문자 구분 매칭).
     */
    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String nfkc = Normalizer.normalize(input, Normalizer.Form.NFKC);
        return nfkc.trim().replaceAll("\\s+", " ");
    }

    /**
     * 코드값/키 비교용 정규화: NFKC + trim + 소문자.
     */
    public static String normalizeKey(String input) {
        return normalize(input).toLowerCase();
    }
}
