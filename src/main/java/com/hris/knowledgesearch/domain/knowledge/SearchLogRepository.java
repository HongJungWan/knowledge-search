package com.hris.knowledgesearch.domain.knowledge;

/**
 * 검색 로그 리포지토리 포트 (PRD §9, DIP).
 * <p>
 * 도메인이 소유하는 순수 인터페이스다. 구현은 infrastructure 에 둔다
 * ({@code infrastructure.persistence.knowledge.SearchLogRepositoryImpl}).
 */
public interface SearchLogRepository {

    /** 검색 로그 저장. */
    SearchLog save(SearchLog searchLog);
}
