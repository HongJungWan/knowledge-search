package com.hris.knowledgesearch.application.evaluation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정답셋 CSV 파싱 + 50/50 계층 구성 검증(CI 게이트 — DB/Docker 불필요).
 */
class GoldDocQueryCsvParserTest {

    @Test
    @DisplayName("gold_search.csv 는 정형/비정형 균형(50/50) 정답셋으로 파싱된다")
    void parsesBalancedGoldSet() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("evaluation/gold_search.csv")) {
            assertThat(in).isNotNull();
            List<GoldDocQuery> gold = GoldDocQueryCsvParser.parse(in);

            assertThat(gold).isNotEmpty();
            long structured = gold.stream().filter(g -> g.kind() == GoldDocQuery.Kind.STRUCTURED).count();
            long unstructured = gold.stream().filter(g -> g.kind() == GoldDocQuery.Kind.UNSTRUCTURED).count();
            assertThat(structured).isEqualTo(unstructured); // 50/50
            assertThat(gold).allSatisfy(g -> assertThat(g.expectedTitleContains()).isNotEmpty());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("정형 행의 codeValues(key=value) 가 파싱된다")
    void parsesCodeValues() {
        List<GoldDocQuery> gold = GoldDocQueryCsvParser.parse(
                getClass().getClassLoader().getResourceAsStream("evaluation/gold_search.csv"));
        GoldDocQuery pending = gold.stream().filter(g -> g.queryId().equals("S1")).findFirst().orElseThrow();
        assertThat(pending.codeValues()).containsEntry("settlement_status", "PENDING");
    }
}
