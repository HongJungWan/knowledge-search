package com.hris.knowledgesearch.infrastructure.persistence.knowledge;

import com.hris.knowledgesearch.domain.knowledge.SearchLog;
import com.hris.knowledgesearch.domain.knowledge.SearchLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 검색 로그 리포지토리 포트의 Redshift 어댑터 (PRD §9, redshift 프로파일 전용).
 * <p>
 * 로그는 INSERT 전용이고 생성 키를 회수할 필요가 없어({@code save} 반환값을 호출자가
 * 쓰지 않음) IDENTITY 자동 채움으로 충분하다. JPA 를 안 쓰는 이유는
 * {@link RedshiftKnowledgeRecordRepositoryImpl} 과 같다.
 */
@Repository
@Profile("redshift")
@RequiredArgsConstructor
public class RedshiftSearchLogRepositoryImpl implements SearchLogRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public SearchLog save(SearchLog searchLog) {
        String sql = "INSERT INTO public.search_log"
                + " (query_raw, query_normalized, tool, latency_ms, hit_count, judged_score)"
                + " VALUES (:queryRaw, :queryNormalized, :tool, :latencyMs, :hitCount, :judgedScore)";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("queryRaw", searchLog.getQueryRaw())
                .addValue("queryNormalized", searchLog.getQueryNormalized())
                .addValue("tool", searchLog.getTool() == null ? null : searchLog.getTool().name())
                .addValue("latencyMs", searchLog.getLatencyMs())
                .addValue("hitCount", searchLog.getHitCount())
                .addValue("judgedScore", searchLog.getJudgedScore());
        jdbcTemplate.update(sql, params);
        return searchLog;
    }
}
