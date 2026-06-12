-- ===================================================================
-- 02. Redshift Spectrum 외부 스키마 + S3 Parquet 외부 테이블 (PRD §4.2)
-- 전제:
--   * Glue Data Catalog 데이터베이스(<GLUE_DATABASE>)와 S3 버킷(<S3_BUCKET>)이 존재
--   * Redshift 클러스터에 Glue/S3 읽기 권한이 있는 IAM 역할(<IAM_ROLE_ARN>) 연결
-- 주의:
--   * 외부 테이블 정의는 Glue Data Catalog 에 저장된다 — Spectrum 은 카탈로그의
--     파티션 메타데이터로 S3 경로를 찾으므로, 새 파티션은 카탈로그에 등록돼야 조회된다.
--     파티션 등록은 ETL 종료 시 GluePartitionRegistrar(AWS SDK glue:CreatePartition)가 수행한다.
--   * 외부 테이블은 SUPER 타입을 선언할 수 없다 → code_values 는 JSON 문자열(VARCHAR).
--     03 뷰에서 네이티브 SUPER 를 JSON_SERIALIZE 해 타입을 맞춘다.
-- ===================================================================

CREATE EXTERNAL SCHEMA IF NOT EXISTS spectrum_knowledge
FROM DATA CATALOG
DATABASE '<GLUE_DATABASE>'
IAM_ROLE '<IAM_ROLE_ARN>'
CREATE EXTERNAL DATABASE IF NOT EXISTS;

CREATE EXTERNAL TABLE spectrum_knowledge.knowledge_archive (
    id                BIGINT,
    domain            VARCHAR(100),
    title             VARCHAR(500),
    body              VARCHAR(65535),
    source_url        VARCHAR(1000),
    code_values       VARCHAR(2000),
    source_updated_at TIMESTAMP,
    content_hash      VARCHAR(64),   -- Parquet 문자열 컬럼은 VARCHAR 선언이 문서 보증 범위 (CHAR 매핑은 불명확)
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    deleted_at        TIMESTAMP
)
PARTITIONED BY (dt DATE)
STORED AS PARQUET
LOCATION 's3://<S3_BUCKET>/knowledge/';
