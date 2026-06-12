# Redshift 마이그레이션 스크립트

운영(redshift 프로파일) 검색 계층의 DDL. H2(local)는 `create-drop` 이라 이 스크립트와 무관하다 (PRD §3.1).

## 실행 순서

| 순서 | 파일 | 내용 |
|---|---|---|
| 1 | `01-knowledge-record.sql` | 네이티브 테이블 `knowledge_record`(SUPER 코드값) + `search_log` |
| 2 | `02-spectrum-external.sql` | Spectrum 외부 스키마(`FROM DATA CATALOG`) + S3 Parquet 외부 테이블(`PARTITIONED BY dt`) |
| 3 | `03-union-view.sql` | 네이티브+외부를 합친 late-binding 뷰 `knowledge_search_v` (단일 SQL 인터페이스) |

플레이스홀더 `<GLUE_DATABASE>` / `<IAM_ROLE_ARN>` / `<S3_BUCKET>` 을 실값으로 치환 후 실행한다.
애플리케이션 쪽 값은 `application-redshift.yml` 의 환경변수로 주입한다.

## 파티션 등록과 조회 누락 방지

Spectrum 은 **Glue Data Catalog 에 등록된 파티션만** 조회한다. 파티션을 등록하지
않으면 해당 구간 데이터가 조회에서 누락된다. 이 레포에서는 ETL Job 완료 시점에
`GluePartitionRegistrar` 가 AWS SDK(`glue:CreatePartition`)로 당일 파티션
(`s3://<S3_BUCKET>/knowledge/dt=YYYY-MM-DD/`)을 등록하고, 이미 있으면
(`AlreadyExistsException`) 멱등으로 넘어간다.

역할 경계: 파티션 경로의 **Parquet 파일 자체는 레이크 파이프라인(외부)이 생산**하고,
이 레포의 ETL 은 Redshift 네이티브 적재 + 해당 일자 파티션의 카탈로그 등록을 책임진다
(PRD §7). 파일보다 등록이 먼저면 빈 파티션(0행)으로 무해하다.

## Redshift 에서 지키는 제약 (기술 노트)

- PRIMARY KEY/UNIQUE 는 **정보성** — 강제되지 않는다. 중복 차단은 ETL(content_hash 조회 후 skip) 책임.
- `knowledge_record.id` 는 IDENTITY — 값이 비연속일 수 있고 JDBC `getGeneratedKeys` 를 지원하지 않는다. 애플리케이션은 키 연속성·생성 키 회수에 의존하지 않는다(INSERT 는 id 생략, 중복 판정은 content_hash). Hibernate 의 IDENTITY 삽입 전략이 동작하지 않는 환경이라 운영 경로는 JPA 대신 JdbcTemplate 어댑터(`RedshiftKnowledgeRecordRepositoryImpl`)를 쓴다.
- 외부 테이블은 SUPER 선언 불가 → `code_values` 는 JSON VARCHAR. 뷰에서 네이티브 SUPER 를 `JSON_SERIALIZE` 로 맞춘다.
- 외부 테이블 참조 뷰는 `WITH NO SCHEMA BINDING` + 완전 수식 이름이 필수.

## 질의 유형 분포 재측정 (90% 의 재현 방법)

코드값/기간 필터로 정규화된 질의 비율 — PRD §1.2 의 '정형 질의 90%' 측정과 동일 기준.

```sql
SELECT CASE WHEN query_normalized SIMILAR TO '%"[a-z0-9_]+":"[A-Za-z0-9_-]+"%'
            OR query_normalized SIMILAR TO '%[0-9]{4}-[0-9]{2}-[0-9]{2}%'
       THEN 'structured' ELSE 'other' END AS bucket,
       COUNT(*) AS cnt
FROM public.search_log
WHERE created_at >= DATEADD(day, -30, GETDATE())
GROUP BY 1;
```
