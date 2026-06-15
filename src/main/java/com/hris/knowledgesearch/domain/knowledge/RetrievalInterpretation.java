package com.hris.knowledgesearch.domain.knowledge;

import com.hris.knowledgesearch.shared.ddd.DomainService;

/**
 * KS→MO 참조 구조 ablation 해석 도메인 서비스.
 * <p>
 * 5개 arm({@link ArmScore})의 정밀도/재현율을 비교해 구조 실효성을 5개 불리언으로 해석한다 —
 * metadata가 정형을 돕는가, vector가 비정형을 돕는가, metadata가 vector 위에 더하는가,
 * 리랭킹이 비정형을 개선하는가, 통합(리랭킹) arm이 최고 정밀도인가.
 * 순수 도메인 로직이다(원시값/도메인 값 객체만). 빈 등록은
 * {@code infrastructure.config.DomainServiceConfig} 의 {@code @Bean}.
 */
@DomainService
public class RetrievalInterpretation {

    public Result interpret(ArmScore keyword, ArmScore meta, ArmScore vector, ArmScore integrated, ArmScore reranked) {
        boolean metadataHelpsStructured = gt(meta.structured().precisionAtK(), keyword.structured().precisionAtK())
                || gt(meta.structured().recallAtK(), keyword.structured().recallAtK());
        boolean vectorHelpsUnstructured = gt(vector.unstructured().recallAtK(), keyword.unstructured().recallAtK())
                || gt(vector.unstructured().precisionAtK(), keyword.unstructured().precisionAtK());
        boolean metadataAddsOverVector = gt(integrated.overall().precisionAtK(), vector.overall().precisionAtK())
                || gt(integrated.overall().recallAtK(), vector.overall().recallAtK());
        boolean rerankImprovesUnstructured = gt(reranked.unstructured().precisionAtK(),
                integrated.unstructured().precisionAtK());
        double bestPrecision = Math.max(Math.max(keyword.overall().precisionAtK(), meta.overall().precisionAtK()),
                Math.max(vector.overall().precisionAtK(), integrated.overall().precisionAtK()));
        boolean integratedBest = reranked.overall().precisionAtK() >= bestPrecision - 1e-9;
        return new Result(metadataHelpsStructured, vectorHelpsUnstructured, metadataAddsOverVector,
                rerankImprovesUnstructured, integratedBest);
    }

    private boolean gt(double a, double b) {
        return a > b + 1e-9;
    }

    public record Result(boolean metadataHelpsStructured, boolean vectorHelpsUnstructured,
                         boolean metadataAddsOverVector, boolean rerankImprovesUnstructured, boolean integratedBest) {
    }
}
