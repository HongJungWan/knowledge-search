package com.hris.knowledgesearch.infrastructure.persistence.knowledge;

import com.hris.knowledgesearch.domain.knowledge.SearchLog;
import com.hris.knowledgesearch.domain.knowledge.SearchLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * 검색 로그 리포지토리 포트의 어댑터 (PRD §9, H2/local 경로).
 * <p>
 * 도메인 포트({@link SearchLogRepository})를 Spring Data JPA({@link SearchLogJpaRepository})로 구현한다.
 * 운영(redshift 프로파일)은 {@link RedshiftSearchLogRepositoryImpl} 이 대신한다.
 */
@Repository
@Profile("!redshift")
@RequiredArgsConstructor
public class SearchLogRepositoryImpl implements SearchLogRepository {

    private final SearchLogJpaRepository jpaRepository;

    @Override
    public SearchLog save(SearchLog searchLog) {
        return jpaRepository.save(searchLog);
    }
}
