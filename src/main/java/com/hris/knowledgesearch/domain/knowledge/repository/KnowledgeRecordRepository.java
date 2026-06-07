package com.hris.knowledgesearch.domain.knowledge.repository;

import com.hris.knowledgesearch.domain.knowledge.entity.KnowledgeRecord;
import com.hris.knowledgesearch.domain.knowledge.repository.custom.KnowledgeRecordRepositoryCustom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 지식 레코드 리포지토리.
 */
public interface KnowledgeRecordRepository
        extends JpaRepository<KnowledgeRecord, Long>, KnowledgeRecordRepositoryCustom {

    /** 콘텐츠 해시로 단건 조회 (ETL 중복 제거에 사용) */
    Optional<KnowledgeRecord> findByContentHash(String contentHash);

    /** 콘텐츠 해시 존재 여부 (ETL upsert skip 판단) */
    boolean existsByContentHash(String contentHash);

    /** 도메인별 스키마 노출용 — 도메인 목록 distinct */
    List<KnowledgeRecord> findByDomain(String domain);
}
