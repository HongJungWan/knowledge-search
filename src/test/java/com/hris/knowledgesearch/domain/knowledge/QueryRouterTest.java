package com.hris.knowledgesearch.domain.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿼리 라우팅(#2) 휴리스틱 단위 테스트.
 * <p>
 * 정형 신호(짧은 키워드구·숫자·코드형)는 MO 경유(true), 긴 자연어는 MO 생략(false).
 * 의심 시 MO 경유로 보수적 편향(정밀도 보존).
 */
class QueryRouterTest {

    private final QueryRouter router = new QueryRouter();

    @Test
    @DisplayName("짧은 키워드구(정형 gold) → MO 경유")
    void shortKeywordPhraseRoutesToMetadata() {
        assertThat(router.shouldResolveViaMetadata("미정산 처리 절차")).isTrue();   // 3 토큰
        assertThat(router.shouldResolveViaMetadata("월정산 내역")).isTrue();        // 2 토큰
        assertThat(router.shouldResolveViaMetadata("위수탁계약 가맹점")).isTrue();   // 2 토큰
    }

    @Test
    @DisplayName("긴 자연어 문장(비정형 gold, 정형 신호 없음) → MO 생략")
    void longNaturalLanguageSkipsMetadata() {
        assertThat(router.shouldResolveViaMetadata("상품을 맡아 팔고 판매분만 수수료로 떼는 계약")).isFalse(); // 7 토큰
        assertThat(router.shouldResolveViaMetadata("아직 확정되지 않아 남아 있는 거래는 어떻게 되나요")).isFalse();
    }

    @Test
    @DisplayName("숫자/날짜 또는 코드형 토큰이 있으면 길어도 MO 경유(보수적)")
    void digitsOrCodeTokensForceMetadata() {
        assertThat(router.shouldResolveViaMetadata("지난달 정산 내역 2026-05 기준 으로 확인")).isTrue(); // 숫자/날짜
        assertThat(router.shouldResolveViaMetadata("정산 상태가 PENDING 인 건 을 모두 보여줘")).isTrue();  // 코드형
    }

    @Test
    @DisplayName("null/blank 는 보수적으로 MO 경유")
    void blankRoutesToMetadata() {
        assertThat(router.shouldResolveViaMetadata(null)).isTrue();
        assertThat(router.shouldResolveViaMetadata("   ")).isTrue();
    }
}
