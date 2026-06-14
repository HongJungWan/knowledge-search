package com.hris.knowledgesearch.infrastructure.embedding;

import com.hris.knowledgesearch.application.knowledge.port.EmbeddingBackfillPort;
import com.hris.knowledgesearch.domain.knowledge.EmbeddingProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 임베딩 백필 어댑터 (postgres 프로파일 전용).
 * <p>
 * {@code embedding IS NULL} 인 활성 레코드를 청크 단위로 읽어 {@code title + body} 를 {@link EmbeddingProvider}
 * 로 임베딩하고 UPDATE 한다. 채운 행은 다음 SELECT 에서 제외되므로 진행성과 멱등성이 보장된다.
 * 어떤 EmbeddingProvider 가 활성이든(해싱 스텁/Ollama bge-m3/Bedrock Titan) 동일하게 동작한다.
 */
@Slf4j
@Component
@Profile("postgres")
public class EmbeddingBackfillService implements EmbeddingBackfillPort {

    private static final int BATCH_SIZE = 100;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final EmbeddingProvider embeddingProvider;

    public EmbeddingBackfillService(NamedParameterJdbcTemplate jdbcTemplate, EmbeddingProvider embeddingProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingProvider = embeddingProvider;
    }

    @Override
    public int backfill() {
        int total = 0;
        while (true) {
            List<Pending> rows = jdbcTemplate.query(
                    "SELECT id, title, body FROM knowledge_record"
                            + " WHERE embedding IS NULL AND deleted_at IS NULL ORDER BY id LIMIT :n",
                    new MapSqlParameterSource("n", BATCH_SIZE),
                    (rs, i) -> new Pending(rs.getLong("id"), rs.getString("title"), rs.getString("body")));
            if (rows.isEmpty()) {
                break;
            }
            for (Pending row : rows) {
                float[] embedding = embeddingProvider.embed(row.text());
                jdbcTemplate.update(
                        "UPDATE knowledge_record SET embedding = CAST(:e AS vector) WHERE id = :id",
                        new MapSqlParameterSource()
                                .addValue("e", VectorLiterals.toLiteral(embedding))
                                .addValue("id", row.id()));
                total++;
            }
        }
        log.info("[EMBEDDING] 백필 완료: {}건", total);
        return total;
    }

    private record Pending(long id, String title, String body) {
        String text() {
            return (title == null ? "" : title) + "\n" + (body == null ? "" : body);
        }
    }
}
