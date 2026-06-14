package com.hris.knowledgesearch.application.evaluation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 검색 지표 계산 단위 테스트(순수 함수 — DB/Docker 불필요, CI 게이트).
 */
class RetrievalMetricsTest {

    private static final double EPS = 1e-4;

    @Test
    @DisplayName("recall@k: 상위 k 내 관련 문서 비율, k 컷오프 적용")
    void recallAtK() {
        List<Boolean> ranked = List.of(false, true, false);
        assertThat(RetrievalMetrics.recallAtK(ranked, 1, 3)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(EPS));
        assertThat(RetrievalMetrics.recallAtK(ranked, 1, 1)).isCloseTo(0.0, org.assertj.core.data.Offset.offset(EPS));
        assertThat(RetrievalMetrics.recallAtK(List.of(true, true), 2, 2))
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(EPS));
        assertThat(RetrievalMetrics.recallAtK(List.of(), 0, 3)).isZero();
    }

    @Test
    @DisplayName("precision@k: 상위 k 중 관련 비율 (정밀도 압력 핵심 지표)")
    void precisionAtK() {
        // 상위 3 중 2건 관련 → 2/3
        assertThat(RetrievalMetrics.precisionAtK(List.of(true, false, true, false), 3))
                .isCloseTo(0.6667, org.assertj.core.data.Offset.offset(EPS));
        // 코드 필터가 완벽히 거르면 상위 k 전부 관련 → 1.0
        assertThat(RetrievalMetrics.precisionAtK(List.of(true, true, true), 3))
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(EPS));
        // 전부 무관 → 0
        assertThat(RetrievalMetrics.precisionAtK(List.of(false, false), 3)).isZero();
    }

    @Test
    @DisplayName("MRR: 첫 관련 문서 순위의 역수, 없으면 0")
    void reciprocalRank() {
        assertThat(RetrievalMetrics.reciprocalRank(List.of(false, true)))
                .isCloseTo(0.5, org.assertj.core.data.Offset.offset(EPS));
        assertThat(RetrievalMetrics.reciprocalRank(List.of(true, false, true)))
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(EPS));
        assertThat(RetrievalMetrics.reciprocalRank(List.of(false, false))).isZero();
    }

    @Test
    @DisplayName("nDCG@k: 이진 이득, 이상 순위 대비 정규화")
    void ndcgAtK() {
        // 관련 문서가 1위 → DCG=IDCG=1 → 1.0
        assertThat(RetrievalMetrics.ndcgAtK(List.of(true, false), 1, 2))
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(EPS));
        // 관련 문서가 2위 → DCG=1/log2(3)=0.6309, IDCG=1 → 0.6309
        assertThat(RetrievalMetrics.ndcgAtK(List.of(false, true), 1, 2))
                .isCloseTo(0.6309, org.assertj.core.data.Offset.offset(EPS));
        assertThat(RetrievalMetrics.ndcgAtK(List.of(false, false), 1, 2)).isZero();
    }
}
