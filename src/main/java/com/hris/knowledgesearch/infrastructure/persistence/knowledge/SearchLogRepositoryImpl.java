package com.hris.knowledgesearch.infrastructure.persistence.knowledge;

import com.hris.knowledgesearch.domain.knowledge.SearchLog;
import com.hris.knowledgesearch.domain.knowledge.SearchLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 검색 로그 리포지토리 포트의 어댑터 (PRD §9).
 * <p>
 * 도메인 포트({@link SearchLogRepository})를 Spring Data JPA({@link SearchLogJpaRepository})로 구현한다.
 */
@Repository
@RequiredArgsConstructor
public class SearchLogRepositoryImpl implements SearchLogRepository {

    private final SearchLogJpaRepository jpaRepository;

    @Override
    public SearchLog save(SearchLog searchLog) {
        return jpaRepository.save(searchLog);
    }
}
