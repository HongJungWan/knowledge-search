package com.hris.knowledgesearch.infrastructure.etl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HashUtil 단위 테스트 (SHA-256 + 중복 제거 판정).
 */
class HashUtilTest {

    @Test
    @DisplayName("SHA-256 은 64자 16진 소문자 해시를 만든다")
    void sha256_format() {
        String hash = HashUtil.sha256("정산 마감 정책");
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("같은 입력은 같은 해시, 다른 입력은 다른 해시 (결정성)")
    void sha256_deterministic() {
        assertThat(HashUtil.sha256("abc")).isEqualTo(HashUtil.sha256("abc"));
        assertThat(HashUtil.sha256("abc")).isNotEqualTo(HashUtil.sha256("abd"));
    }

    @Test
    @DisplayName("알려진 벡터: SHA-256('abc')")
    void sha256_knownVector() {
        assertThat(HashUtil.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("contentHash: 같은 (domain,title,body) 면 중복으로 판정된다")
    void contentHash_dedup() {
        String a = HashUtil.contentHash("SETTLEMENT", "미정산 처리", "본문 내용");
        String b = HashUtil.contentHash("SETTLEMENT", "미정산 처리", "본문 내용");
        String c = HashUtil.contentHash("SETTLEMENT", "정산 보류", "본문 내용");
        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    @DisplayName("null 입력도 빈 문자열로 안전하게 해시한다")
    void sha256_nullSafe() {
        assertThat(HashUtil.sha256(null)).isEqualTo(HashUtil.sha256(""));
    }
}
