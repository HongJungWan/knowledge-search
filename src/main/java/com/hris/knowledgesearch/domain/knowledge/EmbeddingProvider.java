package com.hris.knowledgesearch.domain.knowledge;

/**
 * 임베딩 제공 포트 (DIP).
 * <p>
 * 도메인이 소유하는 순수 인터페이스다. Spring/AWS/HTTP 타입을 시그니처에 노출하지 않는다(primitive {@code float[]} 만).
 * 구현(어댑터)은 infrastructure 에 둔다 — 프로파일로 택1:
 * {@code HashingEmbeddingProvider}(기본/CI 플러밍, 결정론적·의미 없음),
 * {@code LocalModelEmbeddingProvider}(localmodel, Ollama 호환 bge-m3 — 실효성 측정),
 * {@code BedrockEmbeddingProvider}(bedrock, Titan v2 — 향후 운영).
 * <p>
 * 임베딩은 {@link KnowledgeRecord} 엔티티의 필드가 아니라 어댑터({@code PostgresKnowledgeRecordRepositoryImpl})가
 * 소유하는 영속 전용 상태다 — 빈약 엔티티/팩토리 규칙 및 1024-float 의 equals/toString 문제를 피한다.
 */
public interface EmbeddingProvider {

    /**
     * 텍스트의 임베딩을 반환한다. 코사인 유사도용으로 L2 정규화되어 있으며 길이는 {@link #dimension()} 이다.
     *
     * @param text 임베딩 대상 (null/blank 면 영벡터)
     */
    float[] embed(String text);

    /** 임베딩 차원. pgvector 컬럼 {@code vector(N)} 및 {@code embedding.dimension} 설정과 일치해야 한다. */
    int dimension();
}
