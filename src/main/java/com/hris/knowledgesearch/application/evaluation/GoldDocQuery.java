package com.hris.knowledgesearch.application.evaluation;

import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecord;

import java.util.List;
import java.util.Map;

/**
 * 검색 평가 정답셋 한 건.
 * <p>
 * relevance 판정은 두 모드:
 * <ul>
 *   <li><b>제목 모드</b>({@link #expectedTitleContains}): 반환 레코드 title 이 부분문자열을 포함하면 관련.
 *       소규모/특정 문서 정답셋용.</li>
 *   <li><b>코드 모드</b>({@link #expectedCode}, {@code key=value}): 반환 레코드의 {@code code_values} 가 해당 코드를
 *       가지면 관련({@link KnowledgeRecord#hasCodeValue}). <b>정밀도 압력</b> 실험용 — 텍스트가 겹쳐도 코드값으로만
 *       정답이 갈리는 대량 코퍼스에서 코드 필터의 정밀도 기여를 측정한다. {@link #relevantCount} 는 코퍼스 내
 *       해당 코드 문서 수(recall@k 분모).</li>
 * </ul>
 */
public record GoldDocQuery(
        String queryId,
        String query,
        Kind kind,
        List<String> expectedTitleContains,
        Map<String, String> codeValues,
        String expectedCode,
        int relevantCount) {

    /** 질의 유형 — 운영 관찰 50/50 분포의 두 계층. */
    public enum Kind {
        STRUCTURED,
        UNSTRUCTURED
    }

    /** 반환 레코드가 이 정답에 관련되는지 — 코드 모드 우선, 없으면 제목 모드. */
    public boolean isRelevant(KnowledgeRecord record) {
        if (record == null) {
            return false;
        }
        if (expectedCode != null && !expectedCode.isBlank()) {
            int eq = expectedCode.indexOf('=');
            if (eq <= 0) {
                return false;
            }
            String key = expectedCode.substring(0, eq).trim();
            String value = expectedCode.substring(eq + 1).trim();
            // code_values 직렬화 형식(Postgres jsonb::text 는 콜론 뒤 공백 삽입)에 무관하게 판정한다.
            String raw = record.getCodeValues() == null ? null : record.getCodeValues().value();
            String json = raw == null ? "" : raw.replaceAll("\\s+", "");
            return json.contains("\"" + key + "\":\"" + value + "\"");
        }
        return isRelevant(record.getTitle().value());
    }

    /** 제목 부분문자열 포함 여부(제목 모드). */
    public boolean isRelevant(String title) {
        if (title == null) {
            return false;
        }
        return expectedTitleContains.stream().anyMatch(title::contains);
    }

    /** recall@k 분모(총 관련 문서 수) — 코드 모드는 relevantCount, 제목 모드는 기대 토큰 수. */
    public int totalRelevant() {
        if (expectedCode != null && !expectedCode.isBlank()) {
            return relevantCount;
        }
        return expectedTitleContains.size();
    }
}
