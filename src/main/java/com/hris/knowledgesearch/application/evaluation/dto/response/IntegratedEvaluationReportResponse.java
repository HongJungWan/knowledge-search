package com.hris.knowledgesearch.application.evaluation.dto.response;

/**
 * KS→MO 참조 구조 실효성 ablation 리포트 (metadata ON/OFF × vector ON/OFF, 50/50 계층).
 * <p>
 * 4개 arm을 동일 골드셋·동일 검색 포트로 측정해, "온톨로지(MO) 참조가 벡터 위에 추가 값을 더하는가"를
 * 판정한다. {@link Interpretation#metadataAddsOverVector} 가 핵심 — false 이면 이 워크로드에서 KS→MO
 * 2-홉 구조의 추가 기여가 작다는 신호다.
 */
public record IntegratedEvaluationReportResponse(
        int goldCount,
        int k,
        int limit,
        ArmReport keyword,
        ArmReport metadataOnly,
        ArmReport vectorOnly,
        ArmReport integrated,
        double metadataCodeValueCoverage,
        Interpretation interpretation) {

    /** 한 arm(KEYWORD/META/VECTOR/INTEGRATED)의 전체·계층별 지표. */
    public record ArmReport(
            String arm,
            StratumMetrics overall,
            StratumMetrics structured,
            StratumMetrics unstructured) {
    }

    /** 한 계층의 평균 지표. precisionAtK 는 정밀도 압력 실험의 핵심(코드 필터 기여). */
    public record StratumMetrics(
            int queries,
            double precisionAtK,
            double recallAtK,
            double mrr,
            double ndcgAtK) {
    }

    /** 구조 실효성 해석. */
    public record Interpretation(
            boolean metadataHelpsStructured,
            boolean vectorHelpsUnstructured,
            boolean metadataAddsOverVector,
            boolean integratedBest,
            String summary) {
    }
}
