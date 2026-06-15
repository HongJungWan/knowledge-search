package com.hris.knowledgesearch.infrastructure.persistence.knowledge;

import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecord;
import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecordRepository;
import com.hris.knowledgesearch.domain.knowledge.vo.KnowledgeDomain;
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
 * 지식 레코드 리포지토리 포트의 Redshift 어댑터 (PRD §3.1/§4.2/§6, redshift 프로파일 전용).
 * <p>
 * JPA/QueryDSL 대신 {@link NamedParameterJdbcTemplate} 을 쓰는 이유(기술 제약):
 * Redshift 는 PK/UNIQUE 를 강제하지 않고, IDENTITY 가 JDBC {@code getGeneratedKeys} 를
 * 지원하지 않아 Hibernate 의 IDENTITY 삽입 전략이 동작하지 않으며, CLOB 타입이 없다.
 * 읽기·적재 경로를 명시적 SQL 로 두고 H2(local) 경로의 {@link KnowledgeRecordRepositoryImpl}
 * 과 동일한 시맨틱(토큰 OR · 코드값 AND · 완전일치 &gt; 코드값일치 &gt; 부분일치 랭킹 ·
 * 기간 토큰 제외)을 유지한다.
 * <p>
 * 자유 입력 SQL 을 받지 않는다 — 모든 값은 바인딩 파라미터로만 전달하고(인젝션 차단),
 * 코드값 키·값은 화이트리스트({@code [A-Za-z0-9_-]{1,64}})를 추가로 강제해 LIKE
 * 와일드카드({@code %}/{@code _})로 매칭이 왜곡되는 것까지 차단한다.
 * 키워드 토큰의 와일드카드({@code %}/{@code _})는 {@code ESCAPE '!'} 로 리터럴 처리한다 —
 * H2 경로(QueryDSL contains 가 이스케이프함, 실측)와 동일 시맨틱이며
 * {@code KnowledgeSearchIntegrationTest} 가 tripwire 로 고정한다.
 * <p>
 * {@code search.spectrum.enabled=true} 면 <b>검색만</b> 네이티브+Spectrum 외부 테이블 통합 뷰
 * ({@code knowledge_search_v}, scripts/redshift/03)를 조회한다. 단건 조회·중복 판정은
 * 항상 네이티브 테이블이다(메서드 javadoc 참조).
 */
@Repository
@Profile("redshift")
public class RedshiftKnowledgeRecordRepositoryImpl implements KnowledgeRecordRepository {

    private static final String NATIVE_TABLE = "public.knowledge_record";
    private static final String SEARCH_VIEW = "public.knowledge_search_v";
    /** 코드값 키·값 화이트리스트 — LIKE 패턴에 들어가므로 와일드카드·따옴표를 막는다. */
    private static final Pattern SAFE_CODE_TOKEN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private static final RowMapper<KnowledgeRecord> RECORD_MAPPER = (rs, rowNum) -> KnowledgeRecord.builder()
            .id(rs.getLong("id"))
            .domain(new KnowledgeDomain(rs.getString("domain")))
            .title(rs.getString("title"))
            .body(rs.getString("body"))
            .sourceUrl(rs.getString("source_url"))
            .codeValues(rs.getString("code_values"))
            .sourceUpdatedAt(toInstant(rs.getTimestamp("source_updated_at")))
            .contentHash(rs.getString("content_hash"))
            .build();

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final boolean spectrumEnabled;

    public RedshiftKnowledgeRecordRepositoryImpl(
            NamedParameterJdbcTemplate jdbcTemplate,
            @Value("${search.spectrum.enabled:false}") boolean spectrumEnabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.spectrumEnabled = spectrumEnabled;
    }

    @Override
    public List<KnowledgeRecord> search(String domain, String keyword,
                                        Map<String, String> codeValues, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder("deleted_at IS NULL");

        if (StringUtils.hasText(domain)) {
            where.append(" AND domain = :domain");
            params.addValue("domain", domain);
        }
        // 코드값 조건은 한 번만 조립해 매칭(WHERE)과 랭킹(CASE) 양쪽에서 동일하게 쓴다.
        List<String> codeClauses = buildCodeClauses(codeValues, params);
        appendMatchClause(where, params, keyword, codeClauses);

        boolean hasKeyword = StringUtils.hasText(keyword);
        // LIMIT 은 바인딩 대신 검증된 정수를 인라인한다 — Redshift 문서는 LIMIT 에
        // "양의 정수"만 규정하고 파라미터 마커 허용을 보증하지 않는다. int 라 인젝션 불가.
        String sql = "SELECT id, domain, title, body, source_url, "
                + codeValuesExpr() + " AS code_values, source_updated_at, content_hash, "
                + scoreExpr(keyword, codeClauses, params) + " AS score"
                + " FROM " + sourceTable()
                + " WHERE " + where
                + " ORDER BY " + (hasKeyword ? "score DESC, " : "") + "source_updated_at DESC NULLS LAST"
                + " LIMIT " + Math.max(1, limit);

        return jdbcTemplate.query(sql, params, RECORD_MAPPER);
    }

    /**
     * 단건 조회는 항상 네이티브 테이블을 본다 — 통합 뷰는 네이티브/아카이브 양쪽의
     * IDENTITY id 가 겹칠 수 있어 id 단건 조회가 비결정이 된다. ETL 쓰기·MCP get_record 의
     * 대상은 네이티브이므로 시맨틱도 H2 경로(JPA findById)와 일치한다.
     */
    @Override
    public Optional<KnowledgeRecord> findById(Long id) {
        // deleted_at 필터를 SQL 에서 강제한다 — RECORD_MAPPER 는 BaseEntity.deletedAt 을
        // 채울 수 없어 서비스 측 isDeleted() 필터가 이 경로에서는 동작하지 않기 때문.
        String sql = "SELECT id, domain, title, body, source_url, "
                + "JSON_SERIALIZE(code_values) AS code_values, source_updated_at, content_hash"
                + " FROM " + NATIVE_TABLE + " WHERE id = :id AND deleted_at IS NULL";
        List<KnowledgeRecord> result = jdbcTemplate.query(
                sql, new MapSqlParameterSource("id", id), RECORD_MAPPER);
        return result.stream().findFirst();
    }

    /** 중복 판정도 네이티브 고정 — ETL 적재 대상이 네이티브 테이블이기 때문(아카이브는 레이크 파이프라인 소관). */
    @Override
    public boolean existsByContentHash(String contentHash) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM " + NATIVE_TABLE + " WHERE content_hash = :contentHash",
                new MapSqlParameterSource("contentHash", contentHash), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public KnowledgeRecord save(KnowledgeRecord record) {
        // ETL 적재 전용 경로 — 중복은 호출자가 existsByContentHash 로 미리 거른다(PRD §7).
        // id 는 IDENTITY 자동 채움(생략). code_values 는 JSON 문자열을 SUPER 로 파싱해 넣는다.
        // INSERT ... SELECT 형태를 쓰는 이유: Redshift 의 SUPER 적재 문서는 JSON_PARSE 를
        // SELECT 절에서 쓰는 경로를 보증한다 — VALUES 절의 함수+바인딩 조합은 문서 보증 밖.
        String sql = "INSERT INTO " + NATIVE_TABLE
                + " (domain, title, body, source_url, code_values, source_updated_at, content_hash)"
                + " SELECT :domain, :title, :body, :sourceUrl, JSON_PARSE(:codeValues),"
                + " :sourceUpdatedAt, :contentHash";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("domain", record.getDomain().value())
                .addValue("title", record.getTitle())
                .addValue("body", record.getBody())
                .addValue("sourceUrl", record.getSourceUrl())
                .addValue("codeValues", record.getCodeValues())
                .addValue("sourceUpdatedAt", toTimestamp(record.getSourceUpdatedAt()))
                .addValue("contentHash", record.getContentHash());
        jdbcTemplate.update(sql, params);
        return record;
    }

    /** 코드값 AND 조건절 목록을 조립한다(바인딩 파라미터 cv0..N 등록 포함). */
    private List<String> buildCodeClauses(Map<String, String> codeValues, MapSqlParameterSource params) {
        List<String> codeClauses = new ArrayList<>();
        if (codeValues == null) {
            return codeClauses;
        }
        int i = 0;
        for (Map.Entry<String, String> entry : codeValues.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || !StringUtils.hasText(entry.getValue())) {
                continue;
            }
            requireSafeCodeToken(entry.getKey());
            requireSafeCodeToken(entry.getValue());
            String name = "cv" + i++;
            codeClauses.add(codeValuesExpr() + " LIKE :" + name);
            params.addValue(name, "%\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"%");
        }
        return codeClauses;
    }

    /** 키워드 토큰 OR 와 코드값 AND 를 서로 OR 로 결합한다 — H2 경로(QueryDSL)와 동일 시맨틱. */
    private void appendMatchClause(StringBuilder where, MapSqlParameterSource params,
                                   String keyword, List<String> codeClauses) {
        List<String> matchClauses = new ArrayList<>();

        if (StringUtils.hasText(keyword)) {
            List<String> tokenClauses = new ArrayList<>();
            int i = 0;
            for (String token : keyword.trim().split("\\s+")) {
                if (token.isBlank() || isPeriodToken(token)) {
                    continue; // 기간 정규화 토큰(YYYY-MM-DD~YYYY-MM-DD)은 키워드에서 제외
                }
                String name = "kw" + i++;
                tokenClauses.add("(LOWER(title) LIKE :" + name + "Lower ESCAPE '!'"
                        + " OR body LIKE :" + name + " ESCAPE '!')");
                params.addValue(name + "Lower", "%" + escapeLike(token.toLowerCase()) + "%");
                params.addValue(name, "%" + escapeLike(token) + "%");
            }
            if (!tokenClauses.isEmpty()) {
                matchClauses.add("(" + String.join(" OR ", tokenClauses) + ")");
            }
        }

        if (!codeClauses.isEmpty()) {
            matchClauses.add("(" + String.join(" AND ", codeClauses) + ")");
        }

        if (!matchClauses.isEmpty()) {
            where.append(" AND (").append(String.join(" OR ", matchClauses)).append(")");
        }
    }

    /**
     * 랭킹 점수: 완전일치(3) &gt; 코드값일치(2) &gt; 부분일치(1) — H2 경로와 동일 시맨틱.
     * 코드값일치는 행 단위 CASE 분기로 평가한다(요청 단위 상수 가산이 아님 — 그러면 정렬에 무효과다).
     */
    private String scoreExpr(String keyword, List<String> codeClauses, MapSqlParameterSource params) {
        boolean hasKeyword = StringUtils.hasText(keyword);
        boolean hasCode = !codeClauses.isEmpty();

        if (hasKeyword) {
            params.addValue("kwExact", keyword.toLowerCase());
            params.addValue("kwPhraseLower", "%" + keyword.toLowerCase() + "%");
            params.addValue("kwPhrase", "%" + keyword + "%");
            String codeBranch = hasCode
                    ? " WHEN " + String.join(" AND ", codeClauses) + " THEN 2"
                    : "";
            return "CASE WHEN LOWER(title) = :kwExact THEN 3"
                    + codeBranch
                    + " WHEN LOWER(title) LIKE :kwPhraseLower ESCAPE '!'"
                    + " OR body LIKE :kwPhrase ESCAPE '!' THEN 1"
                    + " ELSE 0 END";
        }
        return hasCode ? "2" : "0";
    }

    private String sourceTable() {
        return spectrumEnabled ? SEARCH_VIEW : NATIVE_TABLE;
    }

    /** 네이티브 테이블의 code_values 는 SUPER → LIKE 매칭을 위해 JSON 문자열로 직렬화한다. */
    private String codeValuesExpr() {
        return spectrumEnabled ? "code_values" : "JSON_SERIALIZE(code_values)";
    }

    /** 기간 정규화 토큰(예: 2026-05-01~2026-05-31 또는 2026-05-01) 여부 */
    private boolean isPeriodToken(String token) {
        return token.matches("\\d{4}-\\d{2}-\\d{2}~\\d{4}-\\d{2}-\\d{2}")
                || token.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    /** LIKE 패턴용 이스케이프 — 사용자 키워드의 %/_/! 를 리터럴로 처리(H2/QueryDSL contains 와 동일 시맨틱). */
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
