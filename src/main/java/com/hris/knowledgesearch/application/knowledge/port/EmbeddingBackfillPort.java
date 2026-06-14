package com.hris.knowledgesearch.application.knowledge.port;

/**
 * 임베딩 백필 아웃바운드 포트.
 * <p>
 * 기존 행 중 {@code embedding} 이 비어 있는(NULL) 지식 레코드에 임베딩을 채운다. pgvector 도입 후
 * 이미 적재된 데이터를 한 번 채우거나 모델 교체 후 재임베딩할 때 쓴다. 구현은 infrastructure(postgres 전용).
 */
public interface EmbeddingBackfillPort {

    /** embedding 이 NULL 인 활성 레코드에 임베딩을 채우고 갱신 건수를 반환한다(멱등 — 이미 채워진 행은 건너뜀). */
    int backfill();
}
