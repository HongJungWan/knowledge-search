-- ===================================================================
-- 01. Redshift 네이티브 테이블 (PRD §3.1, §4.3)
-- 실행 주체: Redshift 마이그레이션 스크립트 경로 (Flyway 미사용 — H2 create-drop 과 분리)
-- 주의:
--   * Redshift 의 PRIMARY KEY / UNIQUE 는 정보성(informational)일 뿐 강제되지 않는다.
--     중복 차단은 ETL 적재 단계(content_hash 조회 후 skip)가 책임진다.
--   * id 는 IDENTITY — 값이 비연속일 수 있고 JDBC getGeneratedKeys 를 지원하지 않지만,
--     애플리케이션은 키 연속성·생성 키 회수에 의존하지 않는다(중복 판정은 content_hash,
--     INSERT 는 id 를 생략해 자동 채움). JPA/Hibernate 의 IDENTITY 전략이 Redshift 에서
--     동작하지 않는 이유이기도 하다 — 그래서 운영 경로는 JdbcTemplate 어댑터를 쓴다.
--   * body 는 CLOB 이 없어 VARCHAR(65535)(최대 길이) 로 둔다.
--   * code_values 는 반정형 SUPER — 조회 시 JSON_SERIALIZE 로 직렬화한다(03 뷰).
-- ===================================================================

CREATE TABLE IF NOT EXISTS public.knowledge_record (
    id                BIGINT IDENTITY(1,1),
    domain            VARCHAR(100)   NOT NULL,
    title             VARCHAR(500)   NOT NULL,
    body              VARCHAR(65535) NOT NULL,
    source_url        VARCHAR(1000),
    code_values       SUPER,
    source_updated_at TIMESTAMP,
    content_hash      VARCHAR(64)    NOT NULL,   -- SHA-256 hex 64자 (외부 테이블과 타입 통일 — Parquet 매핑은 VARCHAR 가 문서 보증 범위)
    created_at        TIMESTAMP      NOT NULL DEFAULT SYSDATE,   -- DEFAULT 식은 variable-free expression — SYSDATE 는 공식 문서(Loading default column values)가 DEFAULT 식 안의 형태로 언급
    updated_at        TIMESTAMP,
    deleted_at        TIMESTAMP,
    PRIMARY KEY (id),          -- 정보성 — Redshift 는 강제하지 않음 (플래너 힌트용)
    UNIQUE (content_hash)      -- 정보성 — 중복 차단은 ETL 이 책임
)
-- 분산: domain 은 저카디널리티(현재 SETTLEMENT 단일)라 KEY 분산 시 슬라이스 쏠림(skew) 발생 → AUTO.
-- 정렬: (domain, source_updated_at) 복합 SORTKEY — 도메인 필터 프루닝(zone map) + 최신순 가중(PRD §6).
DISTSTYLE AUTO
SORTKEY (domain, source_updated_at);

CREATE TABLE IF NOT EXISTS public.search_log (
    id               BIGINT IDENTITY(1,1),   -- 로그는 키 회수 불필요 → IDENTITY 허용
    query_raw        VARCHAR(2000),
    query_normalized VARCHAR(2000),
    tool             VARCHAR(100),
    latency_ms       BIGINT,
    hit_count        INTEGER,
    judged_score     INTEGER,
    created_at       TIMESTAMP NOT NULL DEFAULT SYSDATE,
    updated_at       TIMESTAMP,
    deleted_at       TIMESTAMP
)
DISTSTYLE EVEN
SORTKEY (created_at);
