package com.hris.knowledgesearch.domain.knowledge;

import java.util.List;

/**
 * 리랭킹 포트 (DIP, 로드맵 #4).
 * <p>
 * 1차 검색(하이브리드 RRF)으로 모은 후보를 질의에 대한 <b>의미 변별</b> 기준으로 재정렬해 상위 topK 를 돌려준다.
 * bi-encoder 벡터가 의미적으로 유사한 문서(예: 정산 상태가 다르지만 텍스트가 닮은 문서)를 못 가를 때,
 * cross-encoder/LLM-judge 같은 더 강한 신호로 순서를 바로잡는다 — 청킹(granularity)과 직교하는 정밀도 레버.
 * <p>
 * 도메인이 소유하는 순수 인터페이스다(Spring/HTTP 타입 노출 없음). 구현은 infrastructure:
 * {@code NoOpReranker}(기본, 항등+절단) / {@code LlmJudgeReranker}(llmrerank, Ollama LLM 리스트와이즈).
 */
public interface Reranker {

    /**
     * 후보를 질의 관련도 순으로 재정렬해 상위 {@code topK} 를 반환한다.
     *
     * @param query      원본 질의(자연어)
     * @param candidates 1차 검색 후보(랭킹 순)
     * @param topK       반환 개수
     */
    List<KnowledgeRecord> rerank(String query, List<KnowledgeRecord> candidates, int topK);
}
