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

    /**
     * 하이브리드 검색(키워드 + 벡터). {@code queryEmbedding} 이 있으면 벡터 arm 을 RRF 로 융합한다.
     * <p>
     * 기본 구현은 임베딩을 무시하고 4-arg 키워드 검색으로 위임한다 — H2/Redshift 어댑터는 벡터를
     * 지원하지 않으므로 자동으로 키워드 전용으로 강등된다. {@code PostgresKnowledgeRecordRepositoryImpl}
     * 만 이 메서드를 오버라이드해 pgvector RRF 하이브리드를 수행한다(정형 필터=domain·code_values 는
     * 하드 WHERE 로 정밀도 보존, 키워드·벡터 arm 은 그 안에서만 랭킹·융합).
     *
     * @param queryEmbedding 질의 임베딩(L2 정규화, dimension 일치), null 이면 벡터 arm 미적용
     */
    default List<KnowledgeRecord> search(String domain, String keyword, Map<String, String> codeValues,
                                         float[] queryEmbedding, int limit) {
        return search(domain, keyword, codeValues, limit);
    }

    /** ID 로 단건 조회. */
    Optional<KnowledgeRecord> findById(Long id);

    /** 콘텐츠 해시 존재 여부 (ETL insert-or-skip 판단). */
    boolean existsByContentHash(String contentHash);

    /** 지식 레코드 저장. */
    KnowledgeRecord save(KnowledgeRecord record);
}
