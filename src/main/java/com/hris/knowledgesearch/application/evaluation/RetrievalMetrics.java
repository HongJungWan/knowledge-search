package com.hris.knowledgesearch.application.evaluation;

import java.util.List;

/**
 * 검색 품질 지표 계산 유틸(순수 함수 — DB/프레임워크 무관, 단위 테스트로 고정).
 * <p>
 * 입력은 랭킹 순서대로의 관련성 불리언 리스트({@code rankedRelevance})와 해당 질의의 총 관련 문서 수
 * ({@code totalRelevant}). 이진 관련성(relevant=1)을 가정한다.
 */
public final class RetrievalMetrics {

    private RetrievalMetrics() {
    }

    /** precision@k = (상위 k 중 관련 문서 수) / k. 정밀도 압력 실험의 핵심 지표(코드 필터의 정밀도 기여). */
    public static double precisionAtK(List<Boolean> rankedRelevance, int k) {
        if (k <= 0) {
            return 0.0;
        }
        int limit = Math.min(k, rankedRelevance.size());
        int hits = 0;
        for (int i = 0; i < limit; i++) {
            if (rankedRelevance.get(i)) {
                hits++;
            }
        }
        return (double) hits / k;
    }

    /** recall@k = (상위 k 중 관련 문서 수) / (총 관련 문서 수). totalRelevant=0 이면 0. */
    public static double recallAtK(List<Boolean> rankedRelevance, int totalRelevant, int k) {
        if (totalRelevant <= 0) {
            return 0.0;
        }
        int limit = Math.min(k, rankedRelevance.size());
        int hits = 0;
        for (int i = 0; i < limit; i++) {
            if (rankedRelevance.get(i)) {
                hits++;
            }
        }
        return (double) hits / totalRelevant;
    }

    /** MRR(질의 1건) = 1 / (첫 관련 문서의 1-기반 순위). 없으면 0. */
    public static double reciprocalRank(List<Boolean> rankedRelevance) {
        for (int i = 0; i < rankedRelevance.size(); i++) {
            if (rankedRelevance.get(i)) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    /** nDCG@k(이진 이득) = DCG@k / IDCG@k. 이상적 순위는 관련 문서가 앞에 totalRelevant 개. */
    public static double ndcgAtK(List<Boolean> rankedRelevance, int totalRelevant, int k) {
        if (totalRelevant <= 0) {
            return 0.0;
        }
        int limit = Math.min(k, rankedRelevance.size());
        double dcg = 0.0;
        for (int i = 0; i < limit; i++) {
            if (rankedRelevance.get(i)) {
                dcg += 1.0 / log2(i + 2);
            }
        }
        double idcg = 0.0;
        int ideal = Math.min(totalRelevant, k);
        for (int i = 0; i < ideal; i++) {
            idcg += 1.0 / log2(i + 2);
        }
        return idcg == 0 ? 0.0 : dcg / idcg;
    }

    private static double log2(int value) {
        return Math.log(value) / Math.log(2);
    }
}
