package com.hris.knowledgesearch;

import com.hris.knowledgesearch.domain.knowledge.EmbeddingProvider;
import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecordRepository;
import com.hris.knowledgesearch.domain.knowledge.SearchLogRepository;
import com.hris.knowledgesearch.infrastructure.embedding.HashingEmbeddingProvider;
import com.hris.knowledgesearch.infrastructure.persistence.knowledge.PostgresKnowledgeRecordRepositoryImpl;
import com.hris.knowledgesearch.infrastructure.persistence.knowledge.SearchLogRepositoryImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * postgres 프로파일 빈 와이어링 기동 스모크 (pgvector 컨테이너 불필요).
 * <p>
 * 실제 pgvector 마이그레이션(CREATE EXTENSION vector)은 H2 에서 돌지 않으므로 Flyway 를 끄고
 * datasource 를 H2(PostgreSQL 모드)로 가장한다 — 이 테스트는 <b>프로파일 배타성</b>만 검증한다:
 * KnowledgeRecord 포트는 pgvector 하이브리드 어댑터가 단일 선택되고(JPA H2 어댑터는 비활성),
 * SearchLog 는 JPA 어댑터가 그대로 동작하며, EmbeddingProvider 는 기본 해싱 스텁이 선택된다.
 * (실제 하이브리드 SQL·RRF·실효성은 {@code HybridSearchEvaluationIntegrationTest} + 도커 pgvector 가 검증)
 */
@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:postgres-wiring;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@ActiveProfiles("postgres")
class PostgresProfileWiringTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("KnowledgeRecord 포트는 pgvector 하이브리드 어댑터가 단일 선택된다 (JPA H2 어댑터 비활성)")
    void portAdapterIsPostgresHybridImplementation() {
        assertThat(context.getBean(KnowledgeRecordRepository.class))
                .isInstanceOf(PostgresKnowledgeRecordRepositoryImpl.class);
    }

    @Test
    @DisplayName("EmbeddingProvider 는 기본 해싱 스텁이 선택된다 (localmodel/bedrock 미활성)")
    void embeddingProviderDefaultsToHashingStub() {
        assertThat(context.getBean(EmbeddingProvider.class))
                .isInstanceOf(HashingEmbeddingProvider.class);
    }

    @Test
    @DisplayName("SearchLog 는 postgres 에서 JPA 어댑터가 그대로 동작한다")
    void searchLogUsesJpaAdapterOnPostgres() {
        assertThat(context.getBean(SearchLogRepository.class))
                .isInstanceOf(SearchLogRepositoryImpl.class);
    }
}
