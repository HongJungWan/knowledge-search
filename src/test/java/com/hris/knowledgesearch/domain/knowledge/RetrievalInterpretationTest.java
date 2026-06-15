package com.hris.knowledgesearch.domain.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KS→MO ablation 해석 규칙 단위 테스트.
 * <p>
 * gt() 는 epsilon 1e-9 초과만 향상으로 인정한다. integratedBest 는 최고 정밀도 대비 -1e-9 이상.
 */
class RetrievalInterpretationTest {

    private final RetrievalInterpretation interp = new RetrievalInterpretation();

    private ArmScore arm(double oP, double oR, double sP, double sR, double uP, double uR) {
        return new ArmScore(new StratumScore(oP, oR), new StratumScore(sP, sR), new StratumScore(uP, uR));
    }

    @Test
    @DisplayName("각 기여가 명확히 향상이면 모두 true")
    void allContributionsTrue() {
        ArmScore keyword = arm(0.1, 0.1, 0.1, 0.1, 0.1, 0.1);
        ArmScore meta = arm(0.1, 0.1, 0.5, 0.5, 0.1, 0.1);
        ArmScore vector = arm(0.1, 0.1, 0.1, 0.1, 0.5, 0.5);
        ArmScore integrated = arm(0.6, 0.6, 0.1, 0.1, 0.1, 0.1);
        ArmScore reranked = arm(0.9, 0.6, 0.1, 0.1, 0.5, 0.1);

        RetrievalInterpretation.Result r = interp.interpret(keyword, meta, vector, integrated, reranked);
        assertThat(r.metadataHelpsStructured()).isTrue();
        assertThat(r.vectorHelpsUnstructured()).isTrue();
        assertThat(r.metadataAddsOverVector()).isTrue();
        assertThat(r.rerankImprovesUnstructured()).isTrue();
        assertThat(r.integratedBest()).isTrue();
    }

    @Test
    @DisplayName("epsilon 이내 차이는 향상으로 보지 않는다")
    void withinEpsilonNotImprovement() {
        ArmScore keyword = arm(0.5, 0.5, 0.5, 0.5, 0.5, 0.5);
        ArmScore meta = arm(0.5, 0.5, 0.5 + 1e-10, 0.5 + 1e-10, 0.5, 0.5);
        ArmScore vector = arm(0.5, 0.5, 0.5, 0.5, 0.5 + 1e-10, 0.5 + 1e-10);
        ArmScore integrated = arm(0.5 + 1e-10, 0.5 + 1e-10, 0.5, 0.5, 0.5, 0.5);
        ArmScore reranked = arm(0.5, 0.5, 0.5, 0.5, 0.5 + 1e-10, 0.5);

        RetrievalInterpretation.Result r = interp.interpret(keyword, meta, vector, integrated, reranked);
        assertThat(r.metadataHelpsStructured()).isFalse();
        assertThat(r.vectorHelpsUnstructured()).isFalse();
        assertThat(r.metadataAddsOverVector()).isFalse();
        assertThat(r.rerankImprovesUnstructured()).isFalse();
    }

    @Test
    @DisplayName("integratedBest: reranked 가 최고 정밀도 대비 epsilon 이내면 true")
    void integratedBestWithinEpsilon() {
        ArmScore keyword = arm(0.8, 0, 0, 0, 0, 0);
        ArmScore meta = arm(0.7, 0, 0, 0, 0, 0);
        ArmScore vector = arm(0.6, 0, 0, 0, 0, 0);
        ArmScore integrated = arm(0.5, 0, 0, 0, 0, 0);
        ArmScore reranked = arm(0.8 - 1e-10, 0, 0, 0, 0, 0);

        RetrievalInterpretation.Result r = interp.interpret(keyword, meta, vector, integrated, reranked);
        assertThat(r.integratedBest()).isTrue();
    }

    @Test
    @DisplayName("integratedBest: reranked 가 최고 정밀도보다 확연히 낮으면 false")
    void integratedNotBest() {
        ArmScore keyword = arm(0.9, 0, 0, 0, 0, 0);
        ArmScore meta = arm(0.1, 0, 0, 0, 0, 0);
        ArmScore vector = arm(0.1, 0, 0, 0, 0, 0);
        ArmScore integrated = arm(0.1, 0, 0, 0, 0, 0);
        ArmScore reranked = arm(0.5, 0, 0, 0, 0, 0);

        RetrievalInterpretation.Result r = interp.interpret(keyword, meta, vector, integrated, reranked);
        assertThat(r.integratedBest()).isFalse();
    }
}
