package com.hris.knowledgesearch.domain.knowledge;

import com.hris.knowledgesearch.shared.ddd.DomainService;

/**
 * 하이브리드 검색 채택 게이트 도메인 서비스.
 * <p>
 * 채택 규칙: 비정형 재현율이 향상(의미 기여)되고 정형 재현율이 회귀하지 않으면(작은 오차 허용) 채택.
 * 순수 도메인 로직이다(Spring/시계/HTTP 타입 없음 — 원시값만 다룬다). 빈 등록은
 * {@code infrastructure.config.DomainServiceConfig} 의 {@code @Bean}.
 */
@DomainService
public class SearchQualityGate {

    public Verdict evaluate(double keywordUnstructuredRecall, double hybridUnstructuredRecall,
                            double keywordStructuredRecall, double hybridStructuredRecall) {
        boolean unstructuredImproved = hybridUnstructuredRecall > keywordUnstructuredRecall;
        boolean structuredNotRegressed = hybridStructuredRecall >= keywordStructuredRecall - 1e-9;
        return new Verdict(unstructuredImproved, structuredNotRegressed,
                unstructuredImproved && structuredNotRegressed);
    }

    public record Verdict(boolean unstructuredImproved, boolean structuredNotRegressed, boolean pass) {
    }
}
