package com.hris.knowledgesearch.application.evaluation.dto.response;

/**
 * 하이브리드 검색 실효성 평가 리포트 (KEYWORD vs HYBRID, 50/50 계층).
 * <p>
 * 채택 게이트(실효성 판정): 비정형 계층에서 HYBRID 의 recall@k 가 KEYWORD 보다 높고(의미 기여),
 * 정형 계층에서 회귀가 없으면 통과. 통과한 경우에만 하이브리드를 운영 채택한다 — 통과 못 하면
 * 가정(50/50 에서 벡터가 도움) 자체가 기각되며 그 검증이 산출물이다.
 */
public record HybridEvaluationReportResponse(
        int goldCount,
        int k,
        int limit,
        ArmReport keyword,
        ArmReport hybrid,
        GateVerdict gate) {

    /** 한 arm(KEYWORD/HYBRID)의 전체·계층별 지표. */
    public record ArmReport(
            String arm,
            StratumMetrics overall,
            StratumMetrics structured,
            StratumMetrics unstructured) {
    }

    /** 한 계층의 평균 지표. */
    public record StratumMetrics(
            int queries,
            double recallAtK,
            double mrr,
            double ndcgAtK) {
    }

    /** 채택 게이트 판정. */
    public record GateVerdict(
            boolean unstructuredRecallImproved,
            boolean structuredNotRegressed,
            boolean pass,
            String summary) {
    }
}
