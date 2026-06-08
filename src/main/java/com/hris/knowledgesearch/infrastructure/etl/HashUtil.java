package com.hris.knowledgesearch.infrastructure.etl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 콘텐츠 해시 유틸 (PRD §7 중복 제거).
 * <p>
 * 정규화된 본문으로부터 SHA-256 해시를 만든다. 같은 내용은 같은 해시를 갖도록 입력을 그대로 받는다.
 * 순수 함수로 두어 테스트 가능하게 한다.
 */
public final class HashUtil {

    private HashUtil() {
    }

    /**
     * 입력 문자열의 SHA-256 16진 해시(소문자 64자)를 반환한다.
     */
    public static String sha256(String input) {
        if (input == null) {
            input = "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 모든 JVM 에 존재한다. 도달 불가.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * 콘텐츠 해시: domain + title + body 를 합쳐 해시한다 (중복 판정 키).
     */
    public static String contentHash(String domain, String title, String body) {
        String joined = String.join(" ",
                domain == null ? "" : domain,
                title == null ? "" : title,
                body == null ? "" : body);
        return sha256(joined);
    }
}
