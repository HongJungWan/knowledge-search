package com.hris.knowledgesearch.application.evaluation;

import com.hris.knowledgesearch.application.evaluation.dto.response.IntegratedEvaluationReportResponse;
import com.hris.knowledgesearch.application.evaluation.dto.response.IntegratedEvaluationReportResponse.ArmReport;
import com.hris.knowledgesearch.application.evaluation.dto.response.IntegratedEvaluationReportResponse.Interpretation;
import com.hris.knowledgesearch.application.evaluation.dto.response.IntegratedEvaluationReportResponse.StratumMetrics;
import com.hris.knowledgesearch.application.knowledge.port.MetadataResolvePort;
import com.hris.knowledgesearch.application.knowledge.port.MetadataResolveResult;
import com.hris.knowledgesearch.domain.knowledge.EmbeddingProvider;
import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecord;
import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * KS→MO 참조 구조 실효성 ablation 평가.
 * <p>
 * 같은 50/50 정답셋을 4개 arm(metadata ON/OFF × vector ON/OFF)으로 측정한다. metadata ON arm은
 * 골드의 codeValues 컬럼이 아니라 <b>실제 MO {@link MetadataResolvePort#resolve}</b> 결과에서 정규화 질의와
 * 코드값 필터를 얻는다 — 즉 KS→MO 2-홉을 실제로 거친다. 이로써 "온톨로지 참조가 벡터 위에 추가 값을
 * 더하는가"를 정형/비정형 계층별로 판정한다.
 * <p>
 * 의미 있는 수치는 postgres 프로파일 + (localmodel: Ollama bge-m3) + MO 라이브(metadata.enabled=true)에서 나온다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IntegratedEvaluationService {

    private static final String GOLD_RESOURCE = "evaluation/gold_search.csv";

    private final KnowledgeRecordRepository knowledgeRecordRepository;
    private final EmbeddingProvider embeddingProvider;
    private final MetadataResolvePort metadataPort;

    public IntegratedEvaluationReportResponse evaluate(int k, int limit) {
        List<GoldDocQuery> gold = loadGold();

        ArmReport keyword = runArm("KEYWORD", gold, k, limit, false, false);
        ArmReport metadataOnly = runArm("META", gold, k, limit, true, false);
        ArmReport vectorOnly = runArm("VECTOR", gold, k, limit, false, true);
        ArmReport integrated = runArm("INTEGRATED", gold, k, limit, true, true);

        double coverage = metadataCodeValueCoverage(gold);
        Interpretation interpretation = interpret(keyword, metadataOnly, vectorOnly, integrated);

        return new IntegratedEvaluationReportResponse(
                gold.size(), k, limit, keyword, metadataOnly, vectorOnly, integrated, coverage, interpretation);
    }

    private ArmReport runArm(String arm, List<GoldDocQuery> gold, int k, int limit,
                             boolean metaOn, boolean vecOn) {
        List<PerQuery> structured = new ArrayList<>();
        List<PerQuery> unstructured = new ArrayList<>();
        for (GoldDocQuery g : gold) {
            String keyword = g.query();
            Map<String, String> codeValues = Map.of();
            if (metaOn) {
                MetadataResolveResult resolved = metadataPort.resolve(g.query());
                keyword = StringUtils.hasText(resolved.normalizedQuery()) ? resolved.normalizedQuery() : g.query();
                codeValues = extractCodeValues(resolved);
            }
            float[] embedding = vecOn ? embeddingProvider.embed(keyword) : null;
            List<KnowledgeRecord> results =
                    knowledgeRecordRepository.search(null, keyword, codeValues, embedding, limit);

            List<Boolean> relevance = results.stream().map(r -> g.isRelevant(r.getTitle())).toList();
            int totalRelevant = g.expectedTitleContains().size();
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

    /** MO resolve 결과에서 코드값 필터를 추출한다(KnowledgeSearchService.mergeCodeValues 와 동일 규칙). */
    private Map<String, String> extractCodeValues(MetadataResolveResult resolved) {
        Map<String, String> codeValues = new LinkedHashMap<>();
        if (resolved.columnMappings() != null) {
            resolved.columnMappings().stream()
                    .filter(m -> StringUtils.hasText(m.physicalColumn()) && StringUtils.hasText(m.codeValue()))
                    .forEach(m -> codeValues.put(m.physicalColumn(), m.codeValue()));
        }
        return codeValues;
    }

    /** MO가 코드값을 1건 이상 돌려준 질의 비율 — META arm 무기여가 "미해석"인지 "어휘 불일치"인지 구분. */
    private double metadataCodeValueCoverage(List<GoldDocQuery> gold) {
        if (gold.isEmpty()) {
            return 0.0;
        }
        long withCode = gold.stream()
                .filter(g -> !extractCodeValues(metadataPort.resolve(g.query())).isEmpty())
                .count();
        return round((double) withCode / gold.size());
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

    private Interpretation interpret(ArmReport keyword, ArmReport meta, ArmReport vector, ArmReport integrated) {
        boolean metaHelpsStructured = meta.structured().recallAtK() > keyword.structured().recallAtK();
        boolean vectorHelpsUnstructured = vector.unstructured().recallAtK() > keyword.unstructured().recallAtK();
        boolean metadataAddsOverVector = integrated.overall().recallAtK() > vector.overall().recallAtK();
        double best = Math.max(Math.max(keyword.overall().recallAtK(), meta.overall().recallAtK()),
                vector.overall().recallAtK());
        boolean integratedBest = integrated.overall().recallAtK() >= best - 1e-9;
        String summary = String.format(
                "정형 recall@k KEYWORD %.4f→META %.4f(%s) · 비정형 KEYWORD %.4f→VECTOR %.4f(%s) · "
                        + "전체 VECTOR %.4f→INTEGRATED %.4f(MO 추가기여 %s) · INTEGRATED 최상 %s",
                keyword.structured().recallAtK(), meta.structured().recallAtK(),
                metaHelpsStructured ? "기여" : "무기여",
                keyword.unstructured().recallAtK(), vector.unstructured().recallAtK(),
                vectorHelpsUnstructured ? "기여" : "무기여",
                vector.overall().recallAtK(), integrated.overall().recallAtK(),
                metadataAddsOverVector ? "있음" : "없음",
                integratedBest ? "예" : "아니오");
        return new Interpretation(metaHelpsStructured, vectorHelpsUnstructured,
                metadataAddsOverVector, integratedBest, summary);
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

    private record PerQuery(double recall, double mrr, double ndcg) {
    }
}
