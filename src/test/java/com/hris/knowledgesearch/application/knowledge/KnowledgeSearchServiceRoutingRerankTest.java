package com.hris.knowledgesearch.application.knowledge;

import com.hris.knowledgesearch.application.knowledge.port.MetadataResolveResult;
import com.hris.knowledgesearch.application.knowledge.port.MetadataResolvePort;
import com.hris.knowledgesearch.application.knowledge.port.SchemaCatalogPort;
import com.hris.knowledgesearch.domain.knowledge.EmbeddingProvider;
import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecordRepository;
import com.hris.knowledgesearch.domain.knowledge.QueryRouter;
import com.hris.knowledgesearch.domain.knowledge.Reranker;
import com.hris.knowledgesearch.domain.knowledge.SearchLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #2 쿼리 라우팅 + 리랭킹 라이브 연동 동작 검증.
 * <p>
 * 라우팅: ON+비정형(긴 자연어)→MO 미호출, ON+정형(단문)→MO 호출, OFF→항상 호출.
 * 리랭킹: ON→풀 검색 후 reranker 호출, OFF→reranker 미호출.
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeSearchServiceRoutingRerankTest {

    @Mock KnowledgeRecordRepository repo;
    @Mock SearchLogRepository searchLogRepository;
    @Mock MetadataResolvePort metadataPort;
    @Mock SchemaCatalogPort schemaCatalogPort;
    @Mock EmbeddingProvider embeddingProvider;
    @Mock Reranker reranker;

    private KnowledgeSearchService service(boolean routing, boolean reranking, int poolSize) {
        lenient().when(repo.search(any(), anyString(), any(), any(), anyInt())).thenReturn(List.of());
        lenient().when(repo.supportsVectorSearch()).thenReturn(false);
        return new KnowledgeSearchService(repo, searchLogRepository, metadataPort, schemaCatalogPort,
                embeddingProvider, new QueryRouter(), reranker, routing, reranking, poolSize);
    }

    @Test
    @DisplayName("라우팅 ON + 긴 자연어 → MO resolve 미호출(생략)")
    void routingSkipsMetadataForLongNaturalLanguage() {
        service(true, false, 20).search("상품을 맡아 팔고 판매분만 수수료로 떼는 계약", null, null, null);
        verify(metadataPort, never()).resolve(anyString());
    }

    @Test
    @DisplayName("라우팅 ON + 정형 단문 → MO resolve 호출")
    void routingCallsMetadataForStructuredShortQuery() {
        when(metadataPort.resolve(anyString())).thenReturn(MetadataResolveResult.raw("미정산 처리 절차"));
        service(true, false, 20).search("미정산 처리 절차", null, null, null);
        verify(metadataPort).resolve("미정산 처리 절차");
    }

    @Test
    @DisplayName("라우팅 OFF → 긴 자연어라도 항상 MO resolve 호출(현행 동작)")
    void routingDisabledAlwaysCallsMetadata() {
        when(metadataPort.resolve(anyString())).thenReturn(MetadataResolveResult.raw("q"));
        service(false, false, 20).search("상품을 맡아 팔고 판매분만 수수료로 떼는 계약", null, null, null);
        verify(metadataPort).resolve(anyString());
    }

    @Test
    @DisplayName("리랭킹 ON → 풀 크기로 검색 후 reranker.rerank 호출")
    void rerankingFetchesPoolAndReranks() {
        when(metadataPort.resolve(anyString())).thenReturn(MetadataResolveResult.raw("미정산"));
        when(reranker.rerank(anyString(), any(), anyInt())).thenReturn(List.of());
        service(false, true, 20).search("미정산", null, null, 5);  // limit 5, pool=max(5,20)=20
        verify(repo).search(any(), anyString(), any(), any(), eq(20));
        verify(reranker).rerank(eq("미정산"), any(), eq(5));
    }

    @Test
    @DisplayName("리랭킹 OFF → reranker 미호출, 검색은 limit 으로")
    void rerankingDisabledDoesNotRerank() {
        when(metadataPort.resolve(anyString())).thenReturn(MetadataResolveResult.raw("미정산"));
        service(false, false, 20).search("미정산", null, null, 5);
        verify(repo).search(any(), anyString(), any(), any(), eq(5));
        verify(reranker, never()).rerank(anyString(), any(), anyInt());
    }
}
