package com.hris.knowledgesearch.application.evaluation;

import com.hris.knowledgesearch.application.evaluation.dto.response.HybridEvaluationReportResponse;
import com.hris.knowledgesearch.application.evaluation.dto.response.HybridEvaluationReportResponse.ArmReport;
import com.hris.knowledgesearch.application.evaluation.dto.response.HybridEvaluationReportResponse.GateVerdict;
import com.hris.knowledgesearch.application.evaluation.dto.response.HybridEvaluationReportResponse.StratumMetrics;
import com.hris.knowledgesearch.domain.knowledge.EmbeddingProvider;
import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecord;
import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecordRepository;
import com.hris.knowledgesearch.domain.knowledge.SearchQualityGate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 하이브리드 검색 실효성 평가 서비스.
 * <p>
 * 정답셋({@code evaluation/gold_search.csv})의 각 질의를 동일한 도메인 포트로 두 arm 으로 검색한다 —
 * KEYWORD(임베딩 없이 키워드 전용)와 HYBRID(질의 임베딩 + RRF 융합). 두 arm 의 recall@k/MRR/nDCG 를
 * 정형/비정형 계층으로 나눠 비교하고, 비정형 재현율 향상 + 정형 무회귀를 채택 게이트로 판정한다.
 * <p>
 * 의미 있는 수치를 보려면 postgres 프로파일(+localmodel: Ollama bge-m3, 또는 +bedrock)에서 돌려야 한다.
 * 기본 해싱 스텁으로는 배선만 확인된다(수치 무의미). H2/Redshift 에서는 두 arm 이 동일(키워드)해 차이가 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HybridEvaluationService {

    private static final String GOLD_RESOURCE = "evaluation/gold_search.csv";

    private final KnowledgeRecordRepository knowledgeRecordRepository;
    private final EmbeddingProvider embeddingProvider;
    private final SearchQualityGate searchQualityGate;

    /**
     * 정답셋 전체를 평가한다.
     *
     * @param k     지표 컷오프(recall@k/nDCG@k)
     * @param limit arm 당 검색 반환 건수
     */
    public HybridEvaluationReportResponse evaluate(int k, int limit) {
        List<GoldDocQuery> gold = loadGold();
        ArmReport keyword = runArm("KEYWORD", gold, k, limit, false);
        ArmReport hybrid = runArm("HYBRID", gold, k, limit, true);
        return new HybridEvaluationReportResponse(gold.size(), k, limit, keyword, hybrid, verdict(keyword, hybrid));
    }

    private ArmReport runArm(String arm, List<GoldDocQuery> gold, int k, int limit, boolean useVector) {
        List<PerQuery> structured = new ArrayList<>();
        List<PerQuery> unstructured = new ArrayList<>();
        for (GoldDocQuery g : gold) {
            float[] embedding = useVector ? embeddingProvider.embed(g.query()) : null;
            // domain 은 null(전체) — 시드 도메인 표기가 settlement/SETTLEMENT 혼재. code_values 는 정형 필터.
            List<KnowledgeRecord> results = knowledgeRecordRepository.search(
                    null, g.query(), g.codeValues(), embedding, limit);

            List<Boolean> relevance = results.stream().map(r -> g.isRelevant(r)).toList();
            int totalRelevant = g.totalRelevant();
            PerQuery pq = new PerQuery(
                    RetrievalMetrics.recallAtK(relevance, totalRelevant, k),
                    RetrievalMetrics.reciprocalRank(relevance),
                    RetrievalMetrics.ndcgAtK(relevance, totalRelevant, k));
            (g.kind() == GoldDocQuery.Kind.STRUCTURED ? structured : unstructured).add(pq);
        }
        List<PerQuery> all = new ArrayList<>(structured);
        all.addAll(unstructured);
        return new ArmReport(arm, average(all), average(structured), average(unstructured));
    }

    private StratumMetrics average(List<PerQuery> queries) {
        if (queries.isEmpty()) {
            return new StratumMetrics(0, 0, 0, 0);
        }
        double recall = 0;
        double mrr = 0;
        double ndcg = 0;
        for (PerQuery q : queries) {
            recall += q.recall();
            mrr += q.mrr();
            ndcg += q.ndcg();
        }
        int n = queries.size();
        return new StratumMetrics(n, round(recall / n), round(mrr / n), round(ndcg / n));
    }

    private GateVerdict verdict(ArmReport keyword, ArmReport hybrid) {
        // 비정형 재현율 향상(의미 기여) + 정형 무회귀(정밀도 보존, 작은 오차 허용) — 판정 규칙은 도메인 서비스.
        SearchQualityGate.Verdict v = searchQualityGate.evaluate(
                keyword.unstructured().recallAtK(), hybrid.unstructured().recallAtK(),
                keyword.structured().recallAtK(), hybrid.structured().recallAtK());
        boolean unstructuredImproved = v.unstructuredImproved();
        boolean structuredNotRegressed = v.structuredNotRegressed();
        boolean pass = v.pass();
        String summary = String.format(
                "비정형 recall@k %.4f→%.4f(%s), 정형 recall@k %.4f→%.4f(%s) ⇒ %s",
                keyword.unstructured().recallAtK(), hybrid.unstructured().recallAtK(),
                unstructuredImproved ? "향상" : "미향상",
                keyword.structured().recallAtK(), hybrid.structured().recallAtK(),
                structuredNotRegressed ? "무회귀" : "회귀",
                pass ? "채택" : "보류");
        return new GateVerdict(unstructuredImproved, structuredNotRegressed, pass, summary);
    }

    private List<GoldDocQuery> loadGold() {
        InputStream in = getClass().getClassLoader().getResourceAsStream(GOLD_RESOURCE);
        if (in == null) {
            throw new IllegalStateException("정답셋 리소스를 찾을 수 없음: " + GOLD_RESOURCE);
        }
        return GoldDocQueryCsvParser.parse(in);
    }

    private double round(double value) {
        return Math.round(value * 10000) / 10000.0;
    }

    /** 질의 1건의 지표(서비스 내부 홀더). */
    private record PerQuery(double recall, double mrr, double ndcg) {
    }
}
