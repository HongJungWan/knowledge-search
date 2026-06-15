package com.hris.knowledgesearch.domain.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 하이브리드 채택 게이트 단위 테스트.
 * <p>
 * 규칙: 비정형 재현율 향상 + 정형 무회귀(epsilon 1e-9) ⇒ 채택.
 */
class SearchQualityGateTest {

    private final SearchQualityGate gate = new SearchQualityGate();

    @Test
    @DisplayName("비정형 향상 + 정형 무회귀 → 채택")
    void improvedAndNotRegressedPasses() {
        SearchQualityGate.Verdict v = gate.evaluate(0.5, 0.7, 0.6, 0.6);
        assertThat(v.unstructuredImproved()).isTrue();
        assertThat(v.structuredNotRegressed()).isTrue();
        assertThat(v.pass()).isTrue();
    }

    @Test
    @DisplayName("비정형 미향상(동일) → 보류")
    void unstructuredNotImprovedFails() {
        SearchQualityGate.Verdict v = gate.evaluate(0.7, 0.7, 0.6, 0.6);
        assertThat(v.unstructuredImproved()).isFalse();
        assertThat(v.pass()).isFalse();
    }

    @Test
    @DisplayName("정형 회귀 → 보류")
    void structuredRegressedFails() {
        SearchQualityGate.Verdict v = gate.evaluate(0.5, 0.7, 0.6, 0.5);
        assertThat(v.structuredNotRegressed()).isFalse();
        assertThat(v.pass()).isFalse();
    }

    @Test
    @DisplayName("정형 회귀가 epsilon 이내면 무회귀로 본다")
    void structuredWithinEpsilonNotRegressed() {
        SearchQualityGate.Verdict v = gate.evaluate(0.5, 0.7, 0.6, 0.6 - 1e-10);
        assertThat(v.structuredNotRegressed()).isTrue();
        assertThat(v.pass()).isTrue();
    }
}
