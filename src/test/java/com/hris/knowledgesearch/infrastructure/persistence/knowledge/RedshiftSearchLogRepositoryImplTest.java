package com.hris.knowledgesearch.infrastructure.persistence.knowledge;

import com.hris.knowledgesearch.domain.knowledge.SearchLog;
import com.hris.knowledgesearch.domain.knowledge.ToolName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Redshift 검색 로그 어댑터 단위 테스트 (JdbcTemplate 모킹).
 */
@ExtendWith(MockitoExtension.class)
class RedshiftSearchLogRepositoryImplTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("save: search_log INSERT 에 모든 필드를 바인딩한다 (id 는 IDENTITY 자동)")
    void saveInsertsAllFields() {
        var repository = new RedshiftSearchLogRepositoryImpl(jdbcTemplate);
        SearchLog log = SearchLog.record("미정산 가맹점", "정산상태 가맹점",
                ToolName.SEARCH_KNOWLEDGE, 42L, 3);

        repository.save(log);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).update(sql.capture(), params.capture());

        assertThat(sql.getValue()).contains("INSERT INTO public.search_log");
        SqlParameterSource bound = params.getValue();
        assertThat(bound.getValue("queryRaw")).isEqualTo("미정산 가맹점");
        assertThat(bound.getValue("queryNormalized")).isEqualTo("정산상태 가맹점");
        assertThat(bound.getValue("tool")).isEqualTo("SEARCH_KNOWLEDGE");
        assertThat(bound.getValue("latencyMs")).isEqualTo(42L);
        assertThat(bound.getValue("hitCount")).isEqualTo(3);
        assertThat(bound.getValue("judgedScore")).isNull();
    }
}
