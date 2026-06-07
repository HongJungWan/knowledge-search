package com.hris.knowledgesearch.domain.knowledge.repository.custom;

import com.hris.knowledgesearch.domain.knowledge.entity.KnowledgeRecord;

import java.util.List;
import java.util.Map;

/**
 * 지식 레코드 QueryDSL 커스텀 리포지토리 (PRD §6 검색 조립·랭킹).
 */
public interface KnowledgeRecordRepositoryCustom {

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
}
