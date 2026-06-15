package com.hris.knowledgesearch.application.evaluation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 50/50 평가 정답셋({@code gold_search_eval.csv}) 무결성 게이트 (항상 실행, 시맨틱 스택 불필요).
 * <p>
 * 신뢰할 만한 50/50 판정의 데이터 전제조건을 CI 에서 강제한다: 정확히 절반씩의 계층, 모든 행이
 * 코드 모드(expectedCode+relevantCount), 코퍼스 클래스 크기와 일치하는 relevantCount.
 */
class GoldSearchEvalIntegrityTest {

    private static final String GOLD = "evaluation/gold_search_eval.csv";

    private List<GoldDocQuery> load() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(GOLD)) {
            assertThat(in).as("정답셋 리소스 존재").isNotNull();
            return GoldDocQueryCsvParser.parse(in);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("정확히 40개, 정형 20 / 비정형 20 (50/50 구성)")
    void exactlyFiftyFifty() {
        List<GoldDocQuery> gold = load();
        assertThat(gold).hasSize(40);
        Map<GoldDocQuery.Kind, Long> byKind = gold.stream()
                .collect(Collectors.groupingBy(GoldDocQuery::kind, Collectors.counting()));
        assertThat(byKind.get(GoldDocQuery.Kind.STRUCTURED)).isEqualTo(20L);
        assertThat(byKind.get(GoldDocQuery.Kind.UNSTRUCTURED)).isEqualTo(20L);
    }

    @Test
    @DisplayName("모든 행은 코드 모드(expectedCode 설정 + relevantCount=10, 코퍼스 클래스 크기 일치)")
    void everyRowIsCodeModeWithCorpusAlignedCount() {
        List<GoldDocQuery> gold = load();
        assertThat(gold).allSatisfy(g -> {
            assertThat(g.expectedCode()).as("%s expectedCode", g.queryId()).isNotBlank();
            assertThat(g.expectedCode()).contains("=");
            assertThat(g.relevantCount()).as("%s relevantCount = 코퍼스 클래스당 10", g.queryId()).isEqualTo(10);
        });
    }

    @Test
    @DisplayName("코드 차원은 3종(settlement_status/settlement_cycle/contract_type)을 모두 압박한다")
    void coversThreeCodeDimensions() {
        List<GoldDocQuery> gold = load();
        List<String> columns = gold.stream()
                .map(g -> g.expectedCode().substring(0, g.expectedCode().indexOf('=')))
                .distinct()
                .toList();
        assertThat(columns).containsExactlyInAnyOrder("settlement_status", "settlement_cycle", "contract_type");
    }
}
