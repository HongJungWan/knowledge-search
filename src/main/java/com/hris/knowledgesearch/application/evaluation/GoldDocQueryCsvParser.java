package com.hris.knowledgesearch.application.evaluation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 평가 정답셋 CSV 파서.
 * <p>
 * 형식(헤더 1행 + 데이터): {@code queryId,query,kind,expectedTitleContains,codeValues[,expectedCode,relevantCount]}
 * <ul>
 *   <li>{@code expectedTitleContains}: 세미콜론(;) 구분 제목 부분문자열(제목 모드, 코드 모드면 빈칸 허용)</li>
 *   <li>{@code codeValues}: 세미콜론 구분 {@code key=value}(없으면 빈칸)</li>
 *   <li>{@code expectedCode}(선택): {@code key=value} — 코드 기준 relevance(정밀도 압력 실험)</li>
 *   <li>{@code relevantCount}(선택): 코퍼스 내 해당 코드 문서 수(recall@k 분모)</li>
 * </ul>
 * 질의/제목에 쉼표가 없도록 정답셋을 단순 유지한다(쿼우팅 미지원 — 의도적 단순화).
 */
public final class GoldDocQueryCsvParser {

    private GoldDocQueryCsvParser() {
    }

    public static List<GoldDocQuery> parse(InputStream in) {
        List<GoldDocQuery> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                if (header) {
                    header = false;
                    continue;
                }
                result.add(parseLine(line));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("정답셋 CSV 파싱 실패", e);
        }
        return result;
    }

    private static GoldDocQuery parseLine(String line) {
        String[] cols = line.split(",", -1);
        if (cols.length < 4) {
            throw new IllegalArgumentException("정답셋 행 컬럼 부족(>=4): " + line);
        }
        String queryId = cols[0].trim();
        String query = cols[1].trim();
        GoldDocQuery.Kind kind = GoldDocQuery.Kind.valueOf(cols[2].trim().toUpperCase());
        List<String> expected = splitSemicolon(cols[3]);
        Map<String, String> codeValues = parseCodeValues(cols.length > 4 ? cols[4] : "");
        String expectedCode = cols.length > 5 && !cols[5].isBlank() ? cols[5].trim() : null;
        int relevantCount = cols.length > 6 && !cols[6].isBlank() ? Integer.parseInt(cols[6].trim()) : 0;

        // 제목 모드(expectedTitleContains)거나 코드 모드(expectedCode) 중 하나는 있어야 한다.
        if (expected.isEmpty() && expectedCode == null) {
            throw new IllegalArgumentException("expectedTitleContains 또는 expectedCode 중 하나는 필요: " + line);
        }
        return new GoldDocQuery(queryId, query, kind, expected, codeValues, expectedCode, relevantCount);
    }

    private static List<String> splitSemicolon(String raw) {
        List<String> values = new ArrayList<>();
        for (String token : raw.split(";")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private static Map<String, String> parseCodeValues(String raw) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : raw.split(";")) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0 || eq == trimmed.length() - 1) {
                throw new IllegalArgumentException("codeValues 표기는 key=value 이어야 함: " + pair);
            }
            map.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
        }
        return map;
    }
}
