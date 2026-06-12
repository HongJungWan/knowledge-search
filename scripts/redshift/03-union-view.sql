-- ===================================================================
-- 03. 단일 SQL 인터페이스 뷰 — 네이티브 + Spectrum 외부 테이블 통합 (PRD §4.2)
-- 주의:
--   * 외부 테이블을 참조하는 뷰는 late-binding(WITH NO SCHEMA BINDING) 이 필수이고,
--     참조 테이블은 스키마까지 완전 수식(public.x / spectrum_knowledge.y)해야 한다.
--   * 네이티브 쪽 code_values 는 SUPER → JSON_SERIALIZE 로 VARCHAR 화해
--     UNION ALL 양쪽 타입을 맞춘다(외부 테이블 쪽은 이미 JSON VARCHAR).
--   * 외부 테이블의 파티션 컬럼(dt)은 뷰에 노출하지 않는다 — 검색 계층은
--     동일 컬럼 집합 하나만 본다. 파티션 프루닝이 필요한 운영 질의는 외부
--     테이블을 직접 조회한다.
--   * search.spectrum.enabled=true 일 때 검색 어댑터가 이 뷰를 조회한다.
--     (RedshiftKnowledgeRecordRepositoryImpl — false 면 public.knowledge_record)
-- ===================================================================

CREATE OR REPLACE VIEW public.knowledge_search_v AS
SELECT id,
       domain,
       title,
       body,
       source_url,
       JSON_SERIALIZE(code_values) AS code_values,
       source_updated_at,
       content_hash,
       created_at,
       updated_at,
       deleted_at
FROM public.knowledge_record
UNION ALL
SELECT id,
       domain,
       title,
       body,
       source_url,
       code_values,
       source_updated_at,
       content_hash,
       created_at,
       updated_at,
       deleted_at
FROM spectrum_knowledge.knowledge_archive
WITH NO SCHEMA BINDING;
