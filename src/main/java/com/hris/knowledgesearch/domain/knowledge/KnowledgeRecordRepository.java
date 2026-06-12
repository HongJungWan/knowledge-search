package com.hris.knowledgesearch.domain.knowledge;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 지식 레코드 리포지토리 포트 (DIP).
 * <p>
 * 도메인이 소유하는 순수 인터페이스다. Spring/JPA/QueryDSL 타입을 시그니처에 노출하지 않는다.
 * 구현(어댑터)은 infrastructure 에 둔다({@code infrastructure.persistence.knowledge.KnowledgeRecordRepositoryImpl}).
 */
public interface KnowledgeRecordRepository {

    /**
     * 동적 조건으로 지식 레코드를 검색한다.
     * <p>
     * 조건: domain 필터, keyword(title|body contains), code-value 일치.
     * 랭킹: 완전일치 &gt; 코드값일치 &gt; 부분일치, 그 안에서 최신순(source_updated_at desc).
     *
     * @param domain      도메인 필터 (null 이면 전체)
     * @param keyword     키워드 (title/body contains, null/blank 이면 미적용)
     * @param codeValues  코드값 일치 조건 (예: settlement_status=PENDING), 비어 있으면 미적용
     * @param limit       최대 반환 건수 (페이지네이션)
     * @return 랭킹 적용된 레코드 목록
     */
    List<KnowledgeRecord> search(String domain, String keyword, Map<String, String> codeValues, int limit);

    /** ID 로 단건 조회. */
    Optional<KnowledgeRecord> findById(Long id);

    /** 콘텐츠 해시 존재 여부 (ETL insert-or-skip 판단). */
    boolean existsByContentHash(String contentHash);

    /** 지식 레코드 저장. */
    KnowledgeRecord save(KnowledgeRecord record);
}
