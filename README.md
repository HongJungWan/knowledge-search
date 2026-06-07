# knowledge-search

사내 정형 지식 검색(RAG) 백엔드 + **MCP 서버** (정산/Settlement 도메인).
Claude Code 가 MCP 도구 호출로 사내 지식을 직접 검색하고, 그 결과를 근거로 답하게 한다.
벡터 DB 없이 SQL(QueryDSL) 검색으로 운영한다(PRD §1.2).

- **스택**: Java 21 · Spring Boot 3.5.5 · Spring AI MCP Server · JPA · QueryDSL 5.1.0 · Spring Batch · springdoc-openapi 2.7.0 · Caffeine
- **포트**: `8095`
- **루트 패키지**: `com.hris.knowledgesearch`

> 자연어 질의의 정규화·용어→컬럼 매핑은 형제 서비스 **metadata-ontology**(`/api/resolve`, 포트 8096)에 위임한다.
> 기본은 비활성(`metadata.enabled=false`)이라 metadata 없이도 원본 질의로 검색이 동작한다.

## 로컬 실행

기본/LOCAL 프로필은 **H2 in-memory** 로 부팅한다 (외부 의존성·AWS 없음). 부팅 시 `data.sql` 시드가 적재돼 즉시 검색이 된다.

```bash
./gradlew bootRun        # 포트 8095
./gradlew test           # 단위 테스트 (HashUtil, TextNormalizer)
./gradlew compileJava    # 컴파일 검증
```

- Health: http://localhost:8095/health , http://localhost:8095/management/health/liveness
- Swagger UI: http://localhost:8095/swagger-ui.html
- H2 Console: http://localhost:8095/h2-console (JDBC URL `jdbc:h2:mem:knowledgedb`, user `sa`, 빈 비밀번호)

## REST API (MCP 와 동일 기능, 검증·디버깅용)

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/knowledge/search` | 지식 검색 (요약 + 출처) |
| GET  | `/api/knowledge/{id}` | 단건 원문 조회 |
| GET  | `/api/knowledge/schema?domain=SETTLEMENT` | 검색 가능 스키마 설명 |
| POST | `/etl/run` | ETL 적재 잡 수동 트리거 (PRD §7.2) |

```bash
curl -X POST http://localhost:8095/api/knowledge/search \
  -H 'Content-Type: application/json' \
  -d '{"query":"미정산", "domain":"SETTLEMENT", "limit":5}'

curl -X POST http://localhost:8095/etl/run
```

## MCP 도구 (PRD §5)

Spring AI MCP Server(webmvc)로 다음 도구를 노출한다. `KnowledgeSearchTools` 의 `@Tool` 메서드를
`McpConfig` 가 `MethodToolCallbackProvider` 로 등록한다.

| 도구 | 입력 | 출력 |
|---|---|---|
| `search_knowledge` | `query`, `domain?`, `filters?`, `limit?` | 레코드 요약 목록 + 출처 |
| `get_record` | `id` | 원문 전체 + 메타데이터 |
| `list_schema` | `domain?` | 검색 가능한 테이블·컬럼·코드값 |

> **Spring AI 버전 주의**: `spring-ai-bom:1.0.0` + `spring-ai-starter-mcp-server-webmvc` 를 쓴다.
> `@Tool`/`@ToolParam`(`org.springframework.ai.tool.annotation`), `MethodToolCallbackProvider`
> (`org.springframework.ai.tool.method`) 는 1.0.0 기준으로 확인했다. 버전 업그레이드 시 시그니처 재확인 필요
> (`build.gradle`/`McpConfig`/`KnowledgeSearchTools` 의 `// TODO: verify Spring AI version`).

## ETL / 데이터 품질 (PRD §7)

Spring Batch 잡 `settlementIngestionJob` (수동 트리거: `POST /etl/run`, 부팅 자동 실행 OFF).

1. **읽기**: `classpath:sample/settlement-source.json` (의도적 공백/대소문자/중복 + 제목 누락 1건 포함)
2. **검증**: 필수 필드(title/body) 누락 레코드 skip + 로그
3. **정규화**: `TextNormalizer` — trim, 연속 공백 축약, 전각/반각(NFKC)
4. **중복 제거**: `HashUtil` 의 SHA-256 `content_hash` — 같은 내용 1건만 적재
5. **쓰기**: `content_hash` 중복(기존/청크 내) skip 후 upsert

순수 유틸(`HashUtil`, `TextNormalizer`)은 단위 테스트로 검증한다.

## AWS 미연결 (현재 — TODO)

이 프로젝트는 아직 AWS 에 연결되지 않는다. **모든 부팅은 H2** 로 한다.

- **Redshift datasource**: `application.yml` 하단 `# TODO(AWS):` 주석 블록(`prod` 프로필 예시). Redshift JDBC 드라이버(`com.amazon.redshift:redshift-jdbc42`)는 연결 확정 시 `build.gradle` 에 추가한다.
- **Redshift Spectrum / Glue Data Catalog / S3**: `search.spectrum.enabled: false`(기본) 플래그 뒤. 켜도 가드(`// TODO(AWS)`)에 막혀 동작하지 않는다. 외부 테이블/파티션 등록 설정은 `application.yml` 주석 블록 참조.
- **EXPLAIN 선점검**(PRD §6): Redshift 경로 활성화 시 SELECT 전 `EXPLAIN` 점검 — `KnowledgeRecordRepositoryCustomImpl` 의 `TODO(AWS)` 주석 참조. 현재 H2 경로에서는 생략.
- **metadata 연동**: `metadata.enabled: false`(기본). 켜면 `http://localhost:8096/api/resolve` 호출, 실패 시 원본 질의 폴백.
- 자격증명은 환경변수/EC2 환경 파일로 주입한다. **저장소 평문 커밋 금지.**

## 데이터 모델 (PRD §4)

| 엔티티 | 핵심 필드 |
|---|---|
| `KnowledgeRecord` | id, domain, title, body(@Lob), sourceUrl, codeValues(운영 Redshift SUPER / H2 JSON 텍스트), sourceUpdatedAt, contentHash(unique) |
| `SearchLog` | queryRaw, queryNormalized, tool, latencyMs, hitCount, judgedScore(nullable) |

> 운영에서 `KnowledgeRecord` 는 Redshift 네이티브 테이블에 **읽기 위주**로 매핑하고, 쓰기는 ETL/배치로 모은다(PRD §3.1).
> Redshift IDENTITY 는 값의 연속성을 보장하지 않으므로 운영 키는 ETL 에서 생성한 값을 쓴다.
