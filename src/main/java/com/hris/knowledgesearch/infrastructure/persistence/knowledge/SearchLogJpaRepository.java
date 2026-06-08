package com.hris.knowledgesearch.infrastructure.persistence.knowledge;

import com.hris.knowledgesearch.domain.knowledge.SearchLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 검색 로그 Spring Data JPA 리포지토리 (인프라 전용, PRD §9).
 */
public interface SearchLogJpaRepository extends JpaRepository<SearchLog, Long> {
}
