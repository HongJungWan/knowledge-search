package com.hris.knowledgesearch.infrastructure.persistence.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.knowledgesearch.domain.knowledge.EmbeddingProvider;
import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecord;
import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecordRepository;
import com.hris.knowledgesearch.domain.knowledge.vo.KnowledgeDomain;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 지식 레코드 리포지토리 포트의 OpenSearch 어댑터 — opensearch 프로파일 전용(운영 검색 타깃 데모, AWS 불필요).
 * <p>
 * 하이브리드 검색을 <b>BM25 + k-NN 두 질의 + RRF(k=60) 융합</b>으로 수행한다. pgvector 어댑터의 SQL RRF 와
 * 동일 개념을 OpenSearch 로 이관한 것이다(운영 전환 시 OpenSearch 네이티브 hybrid/RRF 파이프라인으로 교체 가능).
 * 정형 필터(domain·code_values)는 두 arm 모두에 {@code filter}(term)로 적용해 정밀도를 보존한다(벡터가 못 넓힘).
 * <p>
 * 저장소가 OpenSearch 이므로 datasource(Postgres)는 SearchLog/배치에만 쓰인다(profile: postgres,opensearch).
 * 임베딩은 어댑터가 적재 시 생성해 {@code knn_vector} 필드에 색인한다.
 */
@Slf4j
@Repository
@Profile("opensearch")
public class OpenSearchKnowledgeRecordRepositoryImpl implements KnowledgeRecordRepository {

    private static final String INDEX = "knowledge_record";
    private static final int MIN_POOL = 50;
    private static final int RRF_K = 60;
    private static final Pattern SAFE_CODE_TOKEN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final EmbeddingProvider embeddingProvider;
    private final int dimension;
    /** title/body 텍스트 분석기. 기본 standard, nori 이미지에선 nori(한국어 형태소) — 로드맵 #7. */
    private final String textAnalyzer;

    public OpenSearchKnowledgeRecordRepositoryImpl(
            ObjectMapper objectMapper,
            EmbeddingProvider embeddingProvider,
            @Value("${opensearch.url:http://localhost:9200}") String url,
            @Value("${embedding.dimension:1024}") int dimension,
            @Value("${opensearch.text-analyzer:standard}") String textAnalyzer) {
        this.client = RestClient.builder().baseUrl(url).build();
        this.objectMapper = objectMapper;
        this.embeddingProvider = embeddingProvider;
        this.dimension = dimension;
        this.textAnalyzer = textAnalyzer;
    }

    /** 인덱스(매핑+knn 설정)를 없으면 생성한다. 이미 있으면 OpenSearch 가 400 을 주며, 무시한다. */
    @PostConstruct
    public void ensureIndex() {
        Map<String, Object> embeddingField = Map.of(
                "type", "knn_vector", "dimension", dimension,
                "method", Map.of("name", "hnsw", "space_type", "cosinesimil", "engine", "lucene"));
        Map<String, Object> mapping = Map.of(
                "settings", Map.of("index", Map.of("knn", true)),
                "mappings", Map.of(
                        "dynamic_templates", List.of(Map.of("code_values_keyword",
                                Map.of("path_match", "code_values.*", "mapping", Map.of("type", "keyword")))),
                        "properties", Map.of(
                                "id", Map.of("type", "long"),
                                "domain", Map.of("type", "keyword"),
                                "title", Map.of("type", "text", "analyzer", textAnalyzer),
                                "body", Map.of("type", "text", "analyzer", textAnalyzer),
                                "source_url", Map.of("type", "keyword"),
                                "code_values", Map.of("type", "object"),
                                "source_updated_at", Map.of("type", "date"),
                                "content_hash", Map.of("type", "keyword"),
                                "embedding", embeddingField)));
        try {
            client.put().uri("/" + INDEX).body(mapping).retrieve().toBodilessEntity();
            log.info("[OpenSearch] 인덱스 생성: {}", INDEX);
        } catch (RuntimeException e) {
            log.info("[OpenSearch] 인덱스 생성 생략(이미 존재 추정): {}", e.getMessage());
        }
    }

    @Override
    public List<KnowledgeRecord> search(String domain, String keyword, Map<String, String> codeValues, int limit) {
        return search(domain, keyword, codeValues, null, limit);
    }

    @Override
    public List<KnowledgeRecord> search(String domain, String keyword, Map<String, String> codeValues,
                                        float[] queryEmbedding, int limit) {
        int effectiveLimit = Math.max(1, limit);
        int pool = Math.max(effectiveLimit, MIN_POOL);
        List<Map<String, Object>> filters = buildFilters(domain, codeValues);
        boolean hasKeyword = StringUtils.hasText(keyword);
        boolean hasVector = queryEmbedding != null && queryEmbedding.length > 0;

        // 키워드 arm(BM25) 랭킹
        List<Hit> kwHits = hasKeyword
                ? searchHits(bm25Query(keyword, filters, pool))
                : searchHits(filteredMatchAll(filters, pool));
        // 벡터 arm(k-NN) 랭킹
        List<Hit> vecHits = hasVector ? searchHits(knnQuery(queryEmbedding, filters, pool)) : List.of();

        // RRF 융합(k=60): 두 arm 의 순위 역수 합. 벡터 없으면 키워드 순서 그대로.
        Map<String, Double> rrf = new LinkedHashMap<>();
        Map<String, Hit> byId = new LinkedHashMap<>();
        accumulate(rrf, byId, kwHits);
        accumulate(rrf, byId, vecHits);

        return rrf.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(effectiveLimit)
                .map(e -> toRecord(byId.get(e.getKey())))
                .toList();
    }

    @Override
    public Optional<KnowledgeRecord> findById(Long id) {
        Map<String, Object> query = Map.of("size", 1, "query", Map.of("term", Map.of("id", id)));
        List<Hit> hits = searchHits(query);
        return hits.isEmpty() ? Optional.empty() : Optional.of(toRecord(hits.get(0)));
    }

    @Override
    public boolean existsByContentHash(String contentHash) {
        JsonNode resp = post("/" + INDEX + "/_count",
                Map.of("query", Map.of("term", Map.of("content_hash", contentHash))));
        return resp != null && resp.path("count").asLong(0) > 0;
    }

    @Override
    public KnowledgeRecord save(KnowledgeRecord record) {
        float[] embedding = embeddingProvider.embed(embedText(record));
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", derivedId(record.getContentHash()));
        doc.put("domain", record.getDomain().value());
        doc.put("title", record.getTitle());
        doc.put("body", record.getBody());
        doc.put("source_url", record.getSourceUrl());
        doc.put("code_values", parseCodeValues(record.getCodeValues()));
        if (record.getSourceUpdatedAt() != null) {
            doc.put("source_updated_at", record.getSourceUpdatedAt().toString());
        }
        doc.put("content_hash", record.getContentHash());
        doc.put("embedding", embedding);
        // _id = content_hash → 멱등 색인(중복 적재 안전). refresh=true 로 즉시 검색 가능.
        client.put().uri("/" + INDEX + "/_doc/" + record.getContentHash() + "?refresh=true")
                .body(doc).retrieve().toBodilessEntity();
        return record;
    }

    // ---- 질의 빌더 ----

    private List<Map<String, Object>> buildFilters(String domain, Map<String, String> codeValues) {
        List<Map<String, Object>> filters = new ArrayList<>();
        if (StringUtils.hasText(domain)) {
            filters.add(Map.of("term", Map.of("domain", domain)));
        }
        if (codeValues != null) {
            for (Map.Entry<String, String> e : codeValues.entrySet()) {
                if (StringUtils.hasText(e.getKey()) && StringUtils.hasText(e.getValue())) {
                    requireSafeCodeToken(e.getKey());
                    requireSafeCodeToken(e.getValue());
                    filters.add(Map.of("term", Map.of("code_values." + e.getKey(), e.getValue())));
                }
            }
        }
        return filters;
    }

    private Map<String, Object> bm25Query(String keyword, List<Map<String, Object>> filters, int pool) {
        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("must", List.of(Map.of("multi_match",
                Map.of("query", keyword, "fields", List.of("title^2", "body")))));
        if (!filters.isEmpty()) {
            bool.put("filter", filters);
        }
        return Map.of("size", pool, "query", Map.of("bool", bool));
    }

    private Map<String, Object> filteredMatchAll(List<Map<String, Object>> filters, int pool) {
        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("must", List.of(Map.of("match_all", Map.of())));
        if (!filters.isEmpty()) {
            bool.put("filter", filters);
        }
        return Map.of("size", pool, "query", Map.of("bool", bool));
    }

    private Map<String, Object> knnQuery(float[] vector, List<Map<String, Object>> filters, int pool) {
        Map<String, Object> knn = new LinkedHashMap<>();
        knn.put("vector", vector);
        knn.put("k", pool);
        if (!filters.isEmpty()) {
            knn.put("filter", Map.of("bool", Map.of("filter", filters)));
        }
        return Map.of("size", pool, "query", Map.of("knn", Map.of("embedding", knn)));
    }

    // ---- HTTP / 파싱 ----

    private List<Hit> searchHits(Map<String, Object> query) {
        JsonNode resp = post("/" + INDEX + "/_search", query);
        List<Hit> hits = new ArrayList<>();
        if (resp == null) {
            return hits;
        }
        for (JsonNode hit : resp.path("hits").path("hits")) {
            hits.add(new Hit(hit.path("_id").asText(), hit.path("_source")));
        }
        return hits;
    }

    private JsonNode post(String uri, Map<String, Object> body) {
        try {
            String json = client.post().uri(uri).body(body).retrieve().body(String.class);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("[OpenSearch] 질의 실패 {}: {}", uri, e.getMessage());
            return null;
        }
    }

    private void accumulate(Map<String, Double> rrf, Map<String, Hit> byId, List<Hit> hits) {
        for (int rank = 0; rank < hits.size(); rank++) {
            Hit h = hits.get(rank);
            rrf.merge(h.id(), 1.0 / (RRF_K + rank + 1), Double::sum);
            byId.putIfAbsent(h.id(), h);
        }
    }

    private KnowledgeRecord toRecord(Hit hit) {
        JsonNode s = hit.source();
        JsonNode codeValues = s.get("code_values");
        String codeValuesJson = (codeValues == null || codeValues.isNull()) ? null : codeValues.toString();
        return KnowledgeRecord.builder()
                .id(s.path("id").isMissingNode() ? null : s.path("id").asLong())
                .domain(new KnowledgeDomain(text(s, "domain")))
                .title(text(s, "title"))
                .body(text(s, "body"))
                .sourceUrl(text(s, "source_url"))
                .codeValues(codeValuesJson)
                .sourceUpdatedAt(parseInstant(text(s, "source_updated_at")))
                .contentHash(text(s, "content_hash"))
                .build();
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** code_values JSON 문자열 → Map(객체 색인용). null/blank 면 빈 맵. */
    private Map<String, Object> parseCodeValues(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String embedText(KnowledgeRecord record) {
        String title = record.getTitle() == null ? "" : record.getTitle();
        String body = record.getBody() == null ? "" : record.getBody();
        return title + "\n" + body;
    }

    /** content_hash(16진)에서 안정적인 long id 파생(데모용 — 충돌 사실상 없음). */
    private static long derivedId(String contentHash) {
        if (contentHash == null || contentHash.length() < 12) {
            return Math.abs(String.valueOf(contentHash).hashCode());
        }
        return Long.parseUnsignedLong(contentHash.substring(0, 12), 16);
    }

    private void requireSafeCodeToken(String value) {
        if (!SAFE_CODE_TOKEN.matcher(value).matches()) {
            throw new IllegalArgumentException("허용되지 않는 코드값 토큰: " + value);
        }
    }

    /** OpenSearch 검색 히트(문서 id + _source). */
    private record Hit(String id, JsonNode source) {
    }
}
