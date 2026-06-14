-- ===================================================================
-- V2 — pgvector 확장 활성화 + 본문 임베딩 컬럼/인덱스 (postgres 프로파일 전용)
-- 차원 1024 = HashingEmbeddingProvider(스텁) · bge-m3(localmodel) · Titan v2(bedrock) 정렬값.
--   embedding.dimension(application.yml) 와 반드시 일치 — 불일치 시 부팅 검증에서 실패한다.
-- 인덱스 HNSW + vector_cosine_ops: 학습단계 없음 · 증분 ETL 삽입 적합 · list 튜닝 불요(IVFFlat 대비 운영 기본값).
-- 주의(db-migration 규칙): 적용된 이 파일은 수정 금지. 차원/모델 변경은 새 V{n}__ 마이그레이션으로 추가한다.
-- embedding 은 어댑터 소유 영속 상태 — JPA 엔티티(KnowledgeRecord)에는 필드로 노출하지 않는다.
-- ===================================================================

CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE knowledge_record ADD COLUMN embedding vector(1024);

CREATE INDEX idx_knowledge_record_embedding
    ON knowledge_record USING hnsw (embedding vector_cosine_ops);
