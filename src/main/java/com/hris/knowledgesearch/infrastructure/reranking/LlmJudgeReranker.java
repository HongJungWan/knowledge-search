package com.hris.knowledgesearch.infrastructure.reranking;

import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecord;
import com.hris.knowledgesearch.domain.knowledge.Reranker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-judge 리스트와이즈 리랭커 ({@code llmrerank} 프로파일) — Ollama 호환 생성 모델(기본 EXAONE 3.5 2.4B, 한국어).
 * <p>
 * 후보를 번호와 함께 제시하고 "관련도 높은 순으로 번호만 나열"하도록 요청해, 응답의 번호 순서로 재정렬한다.
 * bi-encoder 벡터가 못 가르는 의미 유사 문서(텍스트는 닮았으나 상태가 다른 정산 문서)를 LLM 의 변별로 바로잡는다.
 * 질의당 1회 호출(리스트와이즈)로 비용을 억제한다. 파싱 실패/누락 번호는 1차 순서로 보존한다(안전 폴백).
 */
@Component
@Profile("llmrerank")
public class LlmJudgeReranker implements Reranker {

    private static final Pattern BRACKET_NUMBER = Pattern.compile("\\[(\\d+)\\]");
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    // 후보를 변별하려면 본문의 구별 정보가 snippet 에 들어와야 한다(앞부분이 공통 보일러플레이트면 짧으면 안 됨).
    private static final int SNIPPET_CHARS = 800;

    private final RestClient client;
    private final String model;

    public LlmJudgeReranker(
            @Value("${rerank.base-url:${embedding.local.base-url:http://localhost:11434}}") String baseUrl,
            @Value("${rerank.model:exaone3.5:2.4b}") String model) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(120)); // LLM 생성은 느릴 수 있다(CPU)
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.model = model;
    }

    @Override
    public List<KnowledgeRecord> rerank(String query, List<KnowledgeRecord> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (candidates.size() == 1) {
            return candidates.subList(0, Math.min(topK, candidates.size()));
        }

        String prompt = buildPrompt(query, candidates);
        List<Integer> order;
        try {
            GenerateResponse response = client.post().uri("/api/generate")
                    .body(Map.of("model", model, "prompt", prompt, "stream", false))
                    .retrieve().body(GenerateResponse.class);
            order = parseOrder(response == null ? "" : response.response(), candidates.size());
        } catch (RuntimeException e) {
            order = List.of(); // LLM 실패 → 1차 순서 보존
        }

        // LLM 이 매긴 순서 + 누락된 후보를 1차 순서로 보존해 합친다.
        List<KnowledgeRecord> reranked = new ArrayList<>();
        Set<Integer> used = new LinkedHashSet<>();
        for (int idx : order) {
            if (idx >= 0 && idx < candidates.size() && used.add(idx)) {
                reranked.add(candidates.get(idx));
            }
        }
        for (int i = 0; i < candidates.size(); i++) {
            if (used.add(i)) {
                reranked.add(candidates.get(i));
            }
        }
        return reranked.subList(0, Math.min(topK, reranked.size()));
    }

    private String buildPrompt(String query, List<KnowledgeRecord> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("질의에 가장 관련 있는 문서부터 순서대로 [번호] 형식으로 나열하세요. ")
                .append("예: [3] [1] [5]. 관련도가 가장 높은 문서를 맨 앞에 둡니다.\n\n")
                .append("질의: ").append(query).append("\n\n문서:\n");
        for (int i = 0; i < candidates.size(); i++) {
            KnowledgeRecord r = candidates.get(i);
            sb.append('[').append(i + 1).append("] ")
                    .append(safe(r.getTitle())).append(" | ").append(snippet(r.getBody())).append('\n');
        }
        sb.append("\n답(번호 순서):");
        return sb.toString();
    }

    /**
     * 응답에서 문서 순서를 0-기반 인덱스로 추출한다.
     * <p>
     * 모델이 "[번호]" 형태로 답하므로 대괄호 번호를 우선 사용한다(순위마커 "1." 등을 문서번호로 오인하지 않음).
     * 대괄호가 없으면 평문 정수를 폴백으로 쓴다.
     */
    private List<Integer> parseOrder(String response, int size) {
        List<Integer> order = collect(BRACKET_NUMBER.matcher(response), 1, size);
        if (!order.isEmpty()) {
            return order;
        }
        return collect(NUMBER.matcher(response), 0, size);
    }

    private List<Integer> collect(Matcher matcher, int group, int size) {
        List<Integer> order = new ArrayList<>();
        while (matcher.find()) {
            int n = Integer.parseInt(matcher.group(group));
            if (n >= 1 && n <= size) {
                order.add(n - 1);
            }
        }
        return order;
    }

    private static String snippet(String body) {
        if (body == null) {
            return "";
        }
        String oneLine = body.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= SNIPPET_CHARS ? oneLine : oneLine.substring(0, SNIPPET_CHARS);
    }

    private static String safe(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    /** Ollama {@code /api/generate} 응답. */
    record GenerateResponse(String response) {
    }
}
