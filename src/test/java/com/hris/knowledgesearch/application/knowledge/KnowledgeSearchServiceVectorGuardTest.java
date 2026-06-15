package com.hris.knowledgesearch.application.knowledge;

import com.hris.knowledgesearch.application.knowledge.port.MetadataResolveResult;
import com.hris.knowledgesearch.application.knowledge.port.MetadataResolvePort;
import com.hris.knowledgesearch.application.knowledge.port.SchemaCatalogPort;
import com.hris.knowledgesearch.domain.knowledge.EmbeddingProvider;
import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecordRepository;
import com.hris.knowledgesearch.domain.knowledge.QueryRouter;
import com.hris.knowledgesearch.domain.knowledge.Reranker;
import com.hris.knowledgesearch.domain.knowledge.SearchLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R1 회귀 가드: 벡터 미지원 어댑터(H2/Redshift)에서는 응용 서비스가 임베딩 계산을 생략한다.
 * <p>
 * 검색 강등은 의도된 설계이며(포트의 {@code supportsVectorSearch()=false}), 임베딩 비용을
 * 낭비하지 않는 것이 R1 의 핵심 효과다.
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeSearchServiceVectorGuardTest {

    @Mock
    KnowledgeRecordRepository knowledgeRecordRepository;
    @Mock
    SearchLogRepository searchLogRepository;
    @Mock
    MetadataResolvePort metadataPort;
    @Mock
    SchemaCatalogPort schemaCatalogPort;
    @Mock
    EmbeddingProvider embeddingProvider;
    @Mock
    Reranker reranker;

    /** 라우팅·리랭킹 모두 OFF(기본) 서비스. 이 가드는 R1 임베딩 생략만 검증한다. */
    private KnowledgeSearchService newService() {
        return new KnowledgeSearchService(
                knowledgeRecordRepository, searchLogRepository, metadataPort, schemaCatalogPort,
                embeddingProvider, new QueryRouter(), reranker, false, false, 20);
    }

    @Test
    void doesNotEmbedWhenRepositoryHasNoVectorSupport() {
        KnowledgeSearchService service = newService();
        when(metadataPort.resolve(anyString())).thenReturn(MetadataResolveResult.raw("미정산"));
        when(knowledgeRecordRepository.supportsVectorSearch()).thenReturn(false);
        when(knowledgeRecordRepository.search(any(), anyString(), any(), isNull(), anyInt()))
                .thenReturn(List.of());

        service.search("미정산", null, null, null);

        verify(embeddingProvider, never()).embed(anyString());
        verify(knowledgeRecordRepository).search(any(), eq("미정산"), any(), isNull(), anyInt());
    }

    @Test
    void embedsWhenRepositorySupportsVectorSearch() {
        KnowledgeSearchService service = newService();
        float[] vec = new float[] {0.1f, 0.2f};
        when(metadataPort.resolve(anyString())).thenReturn(MetadataResolveResult.raw("미정산"));
        when(knowledgeRecordRepository.supportsVectorSearch()).thenReturn(true);
        when(embeddingProvider.embed(anyString())).thenReturn(vec);
        when(knowledgeRecordRepository.search(any(), anyString(), any(), eq(vec), anyInt()))
                .thenReturn(List.of());

        service.search("미정산", null, null, null);

        verify(embeddingProvider).embed("미정산");
        verify(knowledgeRecordRepository).search(any(), eq("미정산"), any(), eq(vec), anyInt());
    }
}
