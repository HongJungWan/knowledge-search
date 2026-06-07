package com.hris.knowledgesearch.etl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TextNormalizer 단위 테스트 (trim / 공백 축약 / 전각·반각 / 키 정규화).
 */
class TextNormalizerTest {

    @Test
    @DisplayName("앞뒤 공백을 제거한다")
    void trim() {
        assertThat(TextNormalizer.normalize("  정산 정책  ")).isEqualTo("정산 정책");
    }

    @Test
    @DisplayName("연속 공백·탭·개행을 단일 공백으로 축약한다")
    void collapseWhitespace() {
        assertThat(TextNormalizer.normalize("정산\t\t마감   정책\n이월"))
                .isEqualTo("정산 마감 정책 이월");
    }

    @Test
    @DisplayName("전각 문자를 NFKC 로 반각으로 통일한다")
    void fullWidthToHalfWidth() {
        // 전각 'ＡＢＣ１２３' → 반각 'ABC123'
        assertThat(TextNormalizer.normalize("ＡＢＣ１２３")).isEqualTo("ABC123");
    }

    @Test
    @DisplayName("null 은 빈 문자열로 정규화한다")
    void nullSafe() {
        assertThat(TextNormalizer.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("키 정규화는 소문자까지 적용한다")
    void normalizeKey() {
        assertThat(TextNormalizer.normalizeKey("  Settlement_Status  ")).isEqualTo("settlement_status");
    }
}
