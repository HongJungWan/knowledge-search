package com.hris.knowledgesearch.infrastructure.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * QueryDSL 설정 — JPA 어댑터(local/H2 경로) 전용. redshift 프로파일은 JdbcTemplate 어댑터를 써서
 * JPAQueryFactory 빈이 필요 없다(미사용 빈 방지).
 */
@Configuration
@Profile("!redshift")
public class QueryDslConfig {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * JPAQueryFactory Bean 등록.
     */
    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(entityManager);
    }
}
