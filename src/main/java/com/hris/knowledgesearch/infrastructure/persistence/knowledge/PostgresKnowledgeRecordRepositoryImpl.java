package com.hris.knowledgesearch.infrastructure.persistence.knowledge;

import com.hris.knowledgesearch.domain.knowledge.EmbeddingProvider;
import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecord;
import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecordRepository;
import com.hris.knowledgesearch.infrastructure.embedding.TextChunker;
import com.hris.knowledgesearch.infrastructure.embedding.VectorLiterals;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 지식 레코드 리포지토리 포트의 PostgreSQL(pgvector) 어댑터 — postgres 프로파일 전용.
 * <p>
 * 하이브리드 검색(키워드 + 벡터)을 단일 SQL 의 RRF(Reciprocal Rank Fusion, k=60)로 수행한다.
 * <ul>
 *   <li><b>정형 필터(정밀도 불가침)</b>: {@code domain} 과 {@code code_values @> jsonb} 는 {@code filtered}
 *       CTE 의 하드 WHERE 다. 벡터는 결과를 넓히지 못하고 그 필터된 집합 안에서만 재랭킹한다.
 *       순수 코드값 질의(자유 텍스트 없음)는 정형 필터 결과를 정확히 반환한다.</li>
 *   <li><b>키워드 arm</b>: 토큰 OR 매칭 + 완전일치(3)&gt;부분일치(1) 점수, source_updated_at 최신순 → ROW_NUMBER.</li>
 *   <li><b>벡터 arm</b>: {@code embedding <=> CAST(:qvec AS vector)} 코사인 거리순 → ROW_NUMBER(HNSW 인덱스).
 *       {@code queryEmbedding} 이 null 이면(H2/Redshift 와 동일하게) 키워드 전용으로 강등된다.</li>
 * </ul>
 * JdbcTemplate 을 쓰는 이유는 Redshift 어댑터와 동일(pgvector {@code <=>} 연산자는 QueryDSL 로 표현 불가).
 * 자유 입력 SQL 미허용 — 모든 값은 바인딩, 코드값 키·값은 화이트리스트({@code [A-Za-z0-9_-]{1,64}}),
 * 키워드 와일드카드는 {@code ESCAPE '!'} 로 리터럴화한다(H2/Redshift 경로와 동일 시맨틱).
 * <p>
 * 임베딩은 어댑터가 소유하는 영속 전용 상태다 — {@link KnowledgeRecord} 엔티티에는 노출하지 않는다.
 * 신규 적재({@link #save}) 시 {@code title + body} 를 {@link EmbeddingProvider} 로 임베딩해 같은 INSERT 에 기록한다.
 */
@Repository
@Profile("postgres & !opensearch")
public class PostgresKnowledgeRecordRepositoryImpl implements KnowledgeRecordRepository {

    private static final String TABLE = "knowledge_record";
    /** 각 arm 의 후보 풀 크기(요청 limit 보다 넉넉히 — RRF 융합 품질). */
    private static final int MIN_POOL = 50;
    private static final int RRF_K = 60;
    private static final Pattern SAFE_CODE_TOKEN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private static final RowMapper<KnowledgeRecord> RECORD_MAPPER = (rs, rowNum) -> KnowledgeRecord.builder()
            .id(rs.getLong("id"))
            .domain(rs.getString("domain"))
            .title(rs.getString("title"))
            .body(rs.getString("body"))
            .sourceUrl(rs.getString("source_url"))
            .codeValues(rs.getString("code_values"))
            .sourceUpdatedAt(toInstant(rs.getTimestamp("source_updated_at")))
            .contentHash(rs.getString("content_hash"))
            .build();

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final EmbeddingProvider embeddingProvider;
    /** 청킹 검색 모드(로드맵 #3). true 면 적재 시 청크 임베딩을 만들고 벡터 arm 이 청크 단위로 랭킹한다. */
    private final boolean chunkingEnabled;

    public PostgresKnowledgeRecordRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate,
                                                 EmbeddingProvider embeddingProvider,
                                                 @Value("${search.chunking.enabled:false}") boolean chunkingEnabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingProvider = embeddingProvider;
        this.chunkingEnabled = chunkingEnabled;
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
        boolean hasKeyword = StringUtils.hasText(keyword);
        boolean hasVector = queryEmbedding != null && queryEmbedding.length > 0;

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("pool", pool);
        params.addValue("limit", effectiveLimit);

        // 1) filtered: 정형 필터(domain + code_values containment) 하드 WHERE
        StringBuilder filterWhere = new StringBuilder("deleted_at IS NULL");
        if (StringUtils.hasText(domain)) {
            filterWhere.append(" AND domain = :domain");
            params.addValue("domain", domain);
        }
        for (String clause : buildCodeClauses(codeValues, params)) {
            filterWhere.append(" AND ").append(clause);
        }

        // 2) kw arm: 키워드 토큰 OR 매칭 + 점수/최신순 랭크
        String keywordWhere = buildKeywordWhere(keyword, params);
        String scoreExpr = buildScoreExpr(keyword, params);

        StringBuilder sql = new StringBuilder();
        sql.append("WITH filtered AS (")
                .append(" SELECT id, domain, title, body, source_url, CAST(code_values AS text) AS code_values,")
                .append(" source_updated_at, content_hash, embedding")
                .append(" FROM ").append(TABLE)
                .append(" WHERE ").append(filterWhere)
                .append("), kw AS (")
                .append(" SELECT id, ROW_NUMBER() OVER (ORDER BY ").append(scoreExpr)
                .append(" DESC, source_updated_at DESC NULLS LAST) AS rnk")
                .append(" FROM filtered");
        if (keywordWhere != null) {
            sql.append(" WHERE ").append(keywordWhere);
        }
        // ORDER BY rnk: 윈도 LIMIT 가 임의 행이 아니라 상위 랭크 :pool 개를 자르도록 한다.
        sql.append(" ORDER BY rnk LIMIT :pool)");

        if (hasVector) {
            params.addValue("qvec", VectorLiterals.toLiteral(queryEmbedding));
            if (chunkingEnabled) {
                // 청크 단위 벡터 arm: 문서의 최상(min-거리) 청크로 문서를 랭킹한다 → 장문 본문의 희석을 피한다.
                // 정형 필터(filtered) 안의 문서 청크만 본다.
                sql.append(", vec AS (")
                        .append(" SELECT c.record_id AS id,")
                        .append(" ROW_NUMBER() OVER (ORDER BY MIN(c.embedding <=> CAST(:qvec AS vector))) AS rnk")
                        .append(" FROM knowledge_chunk c WHERE c.record_id IN (SELECT id FROM filtered)")
                        .append(" GROUP BY c.record_id")
                        .append(" ORDER BY MIN(c.embedding <=> CAST(:qvec AS vector)) LIMIT :pool)");
            } else {
                sql.append(", vec AS (")
                        .append(" SELECT id, ROW_NUMBER() OVER (ORDER BY embedding <=> CAST(:qvec AS vector)) AS rnk")
                        .append(" FROM filtered WHERE embedding IS NOT NULL")
                        .append(" ORDER BY embedding <=> CAST(:qvec AS vector) LIMIT :pool)");
            }
            sql.append(selectColumns())
                    .append(", COALESCE(1.0/(").append(RRF_K).append(" + kw.rnk), 0)")
                    .append(" + COALESCE(1.0/(").append(RRF_K).append(" + vec.rnk), 0) AS rrf")
                    .append(" FROM filtered f")
                    .append(" LEFT JOIN kw ON kw.id = f.id")
                    .append(" LEFT JOIN vec ON vec.id = f.id")
                    .append(" WHERE kw.id IS NOT NULL OR vec.id IS NOT NULL")
                    .append(" ORDER BY rrf DESC, f.source_updated_at DESC NULLS LAST")
                    .append(" LIMIT :limit");
        } else {
            // 벡터 미적용(키워드 전용): kw 랭크 순서 그대로.
            sql.append(selectColumns())
                    .append(" FROM filtered f JOIN kw ON kw.id = f.id")
                    .append(" ORDER BY kw.rnk LIMIT :limit");
        }

        return jdbcTemplate.query(sql.toString(), params, RECORD_MAPPER);
    }

    @Override
    public Optional<KnowledgeRecord> findById(Long id) {
        String sql = "SELECT id, domain, title, body, source_url, CAST(code_values AS text) AS code_values,"
                + " source_updated_at, content_hash"
                + " FROM " + TABLE + " WHERE id = :id AND deleted_at IS NULL";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), RECORD_MAPPER)
                .stream().findFirst();
    }

    @Override
    public boolean existsByContentHash(String contentHash) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM " + TABLE + " WHERE content_hash = :contentHash",
                new MapSqlParameterSource("contentHash", contentHash), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public KnowledgeRecord save(KnowledgeRecord record) {
        // 신규 행 임베딩: title + body 를 임베딩해 같은 INSERT 에 기록(ETL writer 의 단일 쓰기 경로).
        // created_at 은 DDL DEFAULT now() — JdbcTemplate 경로는 JPA Auditing 을 경유하지 않는다.
        float[] embedding = embeddingProvider.embed(embedText(record));
        String sql = "INSERT INTO " + TABLE
                + " (domain, title, body, source_url, code_values, source_updated_at, content_hash, embedding)"
                + " VALUES (:domain, :title, :body, :sourceUrl, CAST(:codeValues AS jsonb),"
                + " :sourceUpdatedAt, :contentHash, CAST(:embedding AS vector)) RETURNING id";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("domain", record.getDomain())
                .addValue("title", record.getTitle())
                .addValue("body", record.getBody())
                .addValue("sourceUrl", record.getSourceUrl())
                .addValue("codeValues", record.getCodeValues())
                .addValue("sourceUpdatedAt", toTimestamp(record.getSourceUpdatedAt()))
                .addValue("contentHash", record.getContentHash())
                .addValue("embedding", VectorLiterals.toLiteral(embedding));
        Long id = jdbcTemplate.queryForObject(sql, params, Long.class);
        if (chunkingEnabled && id != null) {
            saveChunks(id, record);
        }
        return record;
    }

    /** 본문을 문장 청크로 나눠 청크별 임베딩을 knowledge_chunk 에 적재한다(청킹 모드). */
    private void saveChunks(long recordId, KnowledgeRecord record) {
        List<String> chunks = TextChunker.chunk(record.getTitle(), record.getBody());
        for (int i = 0; i < chunks.size(); i++) {
            float[] chunkEmbedding = embeddingProvider.embed(chunks.get(i));
            jdbcTemplate.update(
                    "INSERT INTO knowledge_chunk (record_id, chunk_index, content, embedding)"
                            + " VALUES (:rid, :idx, :content, CAST(:embedding AS vector))",
                    new MapSqlParameterSource()
                            .addValue("rid", recordId)
                            .addValue("idx", i)
                            .addValue("content", chunks.get(i))
                            .addValue("embedding", VectorLiterals.toLiteral(chunkEmbedding)));
        }
    }

    /** 임베딩 입력 텍스트(제목 + 본문). */
    private static String embedText(KnowledgeRecord record) {
        String title = record.getTitle() == null ? "" : record.getTitle();
        String body = record.getBody() == null ? "" : record.getBody();
        return title + "\n" + body;
    }

    private String selectColumns() {
        return " SELECT f.id, f.domain, f.title, f.body, f.source_url, f.code_values,"
                + " f.source_updated_at, f.content_hash";
    }

    /** 코드값 AND 컨테인먼트 절 목록(바인딩 cv0..N 등록). 키·값 화이트리스트 강제. */
    private List<String> buildCodeClauses(Map<String, String> codeValues, MapSqlParameterSource params) {
        List<String> clauses = new ArrayList<>();
        if (codeValues == null) {
            return clauses;
        }
        int i = 0;
        for (Map.Entry<String, String> entry : codeValues.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || !StringUtils.hasText(entry.getValue())) {
                continue;
            }
            requireSafeCodeToken(entry.getKey());
            requireSafeCodeToken(entry.getValue());
            String name = "cv" + i++;
            clauses.add("code_values @> CAST(:" + name + " AS jsonb)");
            params.addValue(name, "{\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"}");
        }
        return clauses;
    }

    /** 키워드 토큰 OR 매칭 절(기간 토큰 제외). 키워드 없으면 null(=전체 후보). */
    private String buildKeywordWhere(String keyword, MapSqlParameterSource params) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        List<String> tokenClauses = new ArrayList<>();
        int i = 0;
        for (String token : keyword.trim().split("\\s+")) {
            if (token.isBlank() || isPeriodToken(token)) {
                continue;
            }
            String name = "kw" + i++;
            tokenClauses.add("(title ILIKE :" + name + " ESCAPE '!' OR body LIKE :" + name + " ESCAPE '!')");
            params.addValue(name, "%" + escapeLike(token) + "%");
        }
        return tokenClauses.isEmpty() ? null : "(" + String.join(" OR ", tokenClauses) + ")";
    }

    /** 점수: 완전일치(title) 3 &gt; 부분일치 1 &gt; 0. 코드값은 이미 하드 필터라 점수 분기에서 제외. */
    private String buildScoreExpr(String keyword, MapSqlParameterSource params) {
        if (!StringUtils.hasText(keyword)) {
            return "0";
        }
        params.addValue("kwExact", keyword.trim().toLowerCase());
        params.addValue("kwPhrase", "%" + escapeLike(keyword.trim()) + "%");
        return "CASE WHEN LOWER(title) = :kwExact THEN 3"
                + " WHEN title ILIKE :kwPhrase ESCAPE '!' OR body LIKE :kwPhrase ESCAPE '!' THEN 1"
                + " ELSE 0 END";
    }

    private boolean isPeriodToken(String token) {
        return token.matches("\\d{4}-\\d{2}-\\d{2}~\\d{4}-\\d{2}-\\d{2}")
                || token.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private void requireSafeCodeToken(String value) {
        if (!SAFE_CODE_TOKEN.matcher(value).matches()) {
            throw new IllegalArgumentException("허용되지 않는 코드값 토큰: " + value);
        }
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
