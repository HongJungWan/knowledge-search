package com.hris.knowledgesearch.infrastructure.persistence.knowledge;

import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecord;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 지식 레코드 Spring Data JPA 리포지토리 (인프라 전용).
 * <p>
 * 도메인 포트({@code KnowledgeRecordRepository})의 어댑터({@code KnowledgeRecordRepositoryImpl})가
 * 내부적으로 사용하는 영속성 기술 빈이다. 도메인/응용은 이 타입을 직접 참조하지 않는다.
 */
public interface KnowledgeRecordJpaRepository extends JpaRepository<KnowledgeRecord, Long> {

    /** 콘텐츠 해시 존재 여부 (ETL upsert skip 판단). */
    boolean existsByContentHash(String contentHash);
}
