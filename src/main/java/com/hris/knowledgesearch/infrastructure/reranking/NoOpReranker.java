package com.hris.knowledgesearch.infrastructure.reranking;

import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecord;
import com.hris.knowledgesearch.domain.knowledge.Reranker;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 기본 리랭커 — {@code llmrerank} 프로파일이 아닐 때 활성. 재정렬 없이 1차 순서 그대로 상위 topK 를 자른다.
 * 리랭킹 비활성 환경(운영 기본·CI)에서 RERANKED arm 이 INTEGRATED 와 동일하게 동작하게 한다.
 */
@Component
@Profile("!llmrerank")
public class NoOpReranker implements Reranker {

    @Override
    public List<KnowledgeRecord> rerank(String query, List<KnowledgeRecord> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.subList(0, Math.min(topK, candidates.size()));
    }
}
