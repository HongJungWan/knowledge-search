package com.hris.knowledgesearch.domain.knowledge.repository;

import com.hris.knowledgesearch.domain.knowledge.entity.SearchLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 검색 로그 리포지토리 (PRD §9).
 */
public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {
}
