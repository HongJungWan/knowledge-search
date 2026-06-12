package com.hris.knowledgesearch.infrastructure.persistence.knowledge;

import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redshift 검색 어댑터 단위 테스트 (JdbcTemplate 모킹 — AWS 불필요).
 * <p>
 * H2 경로(QueryDSL)와 동일한 검색 시맨틱(토큰 OR · 코드값 AND · 랭킹 · 기간 토큰 제외)이
 * SQL 문자열과 바인딩 파라미터로 옮겨졌는지, 코드값 화이트리스트가 강제되는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class RedshiftKnowledgeRecordRepositoryImplTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("네이티브 경로: 랭킹/토큰/코드값 바인딩 + 기간 토큰 제외")
    void searchNativePathBuildsRankedSqlWithBindings() {
        var repository = new RedshiftKnowledgeRecordRepositoryImpl(jdbcTemplate, false);

        repository.search("SETTLEMENT", "정산 2026-05-01~2026-05-31",
                Map.of("settlement_status", "PENDING"), 5);

        ArgumentCaptor<String> selectSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).query(selectSql.capture(), params.capture(), any(RowMapper.class));

        assertThat(selectSql.getValue())
                .contains("FROM public.knowledge_record")
                .contains("deleted_at IS NULL")
                .contains("JSON_SERIALIZE(code_values) LIKE :cv0")
                // 코드값일치(2)는 행 단위 CASE 분기로 채점된다 (요청 단위 상수 가산이면 정렬 무효과)
                .contains("WHEN JSON_SERIALIZE(code_values) LIKE :cv0 THEN 2")
                // 키워드 LIKE 는 ESCAPE '!' 로 와일드카드 리터럴 처리 (H2/QueryDSL 와 동일 시맨틱)
                .contains("LIKE :kw0Lower ESCAPE '!'")
                .contains("ORDER BY score DESC, source_updated_at DESC NULLS LAST")
                // LIMIT 은 바인딩이 아니라 검증된 정수 인라인 (Redshift 문서가 파라미터 마커를 보증 안 함)
                .contains("LIMIT 5");

        SqlParameterSource bound = params.getValue();
        assertThat(bound.getValue("domain")).isEqualTo("SETTLEMENT");
        assertThat(bound.getValue("kw0Lower")).isEqualTo("%정산%");
        assertThat(bound.getValue("cv0")).isEqualTo("%\"settlement_status\":\"PENDING\"%");
        assertThat(bound.hasValue("kw1")).as("기간 정규화 토큰은 키워드에서 제외").isFalse();
    }

    @Test
    @DisplayName("Spectrum 경로: 통합 뷰(knowledge_search_v) 조회 — SUPER 직렬화 불필요")
    void searchSpectrumPathUsesUnifiedView() {
        var repository = new RedshiftKnowledgeRecordRepositoryImpl(jdbcTemplate, true);

        repository.search(null, "정산", null, 10);

        ArgumentCaptor<String> selectSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(selectSql.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        assertThat(selectSql.getValue())
                .contains("FROM public.knowledge_search_v")
                .doesNotContain("JSON_SERIALIZE");
    }

    @Test
    @DisplayName("코드값 화이트리스트: LIKE 와일드카드·따옴표가 든 키/값은 거부")
    void searchRejectsUnsafeCodeToken() {
        var repository = new RedshiftKnowledgeRecordRepositoryImpl(jdbcTemplate, false);

        assertThatThrownBy(() ->
                repository.search(null, null, Map.of("settlement_status", "PEND%ING"), 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                repository.search(null, null, Map.of("bad\"key", "PENDING"), 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("save: id 생략(IDENTITY 자동) + INSERT...SELECT JSON_PARSE 로 SUPER 적재")
    void saveInsertsWithJsonParseAndWithoutId() {
        var repository = new RedshiftKnowledgeRecordRepositoryImpl(jdbcTemplate, false);
        KnowledgeRecord record = KnowledgeRecord.forIngestion(
                "SETTLEMENT", "정산 주기 정책", "본문",
                "https://wiki/settlement", "{\"settlement_status\":\"PENDING\"}",
                Instant.parse("2026-06-01T00:00:00Z"), "a".repeat(64));

        repository.save(record);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).update(sql.capture(), params.capture());

        assertThat(sql.getValue())
                .contains("INSERT INTO public.knowledge_record")
                .contains("(domain, title, body, source_url, code_values, source_updated_at, content_hash)")
                // VALUES 절의 함수+바인딩은 Redshift 문서 보증 밖 → INSERT...SELECT 형태를 고정
                .contains("SELECT :domain")
                .contains("JSON_PARSE(:codeValues)")
                .doesNotContain(") VALUES");
        assertThat(params.getValue().getValue("contentHash")).isEqualTo("a".repeat(64));
        assertThat(params.getValue().getValue("codeValues"))
                .isEqualTo("{\"settlement_status\":\"PENDING\"}");
    }

    @Test
    @DisplayName("existsByContentHash: COUNT > 0 이면 true")
    void existsByContentHashReturnsTrueWhenCountPositive() {
        var repository = new RedshiftKnowledgeRecordRepositoryImpl(jdbcTemplate, false);
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class)))
                .thenReturn(1);

        assertThat(repository.existsByContentHash("a".repeat(64))).isTrue();
    }

    @Test
    @DisplayName("existsByContentHash: COUNT 가 0 이면 false")
    void existsByContentHashReturnsFalseWhenMissing() {
        var repository = new RedshiftKnowledgeRecordRepositoryImpl(jdbcTemplate, false);
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class)))
                .thenReturn(0);

        assertThat(repository.existsByContentHash("a".repeat(64))).isFalse();
    }

    @Test
    @DisplayName("existsByContentHash: COUNT 가 null 이면 false")
    void existsByContentHashReturnsFalseWhenNull() {
        var repository = new RedshiftKnowledgeRecordRepositoryImpl(jdbcTemplate, false);
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class)))
                .thenReturn(null);

        assertThat(repository.existsByContentHash("a".repeat(64))).isFalse();
    }
}
