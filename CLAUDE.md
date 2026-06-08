# CLAUDE.md — knowledge-search

> 상세 설계는 `.claude/docs/prd-knowledge-search.md`. 이 파일은 **작업 원칙 + 빠른 참조**만 둔다(중복 금지).

## Working Principles — Karpathy 4원칙 (최우선)

> [Andrej Karpathy의 LLM 코딩 함정 관찰](https://github.com/forrestchang/andrej-karpathy-skills) 기반. 이 프로젝트의 *모든* 코드 작업에 기본 적용. 사소한 작업은 우회 가능하나, 의심스러우면 먼저 적용한다. **속도보다 신중함** 쪽으로 편향.

### 1. 코딩 전에 생각하라
가정은 명시하고 불확실하면 질문한다(`AskUserQuestion`). 해석이 여럿이면 *모두* 제시하고 임의로 고르지 않는다. 더 단순한 길이 있으면 push back 한다. 불분명하면 멈추고, 무엇이 혼란스러운지 명명한다.

### 2. 단순함 우선
문제를 푸는 *최소* 코드만 쓴다. 요청 없는 기능·일회성 추상화·불필요한 "유연성"·발생 불가능한 시나리오 방어코드는 넣지 않는다. 과설계 같으면 다시 쓴다.

### 3. 외과적 변경
꼭 필요한 곳만 건드린다. 인접 코드·주석·포맷을 임의로 "개선"하거나 멀쩡한 것을 리팩토링하지 않는다. *기존 스타일*을 따른다. 본인 변경으로 생긴 고아 코드만 정리하고, 원래부터 있던 dead code는 언급만 한다.

### 4. 목표 주도 실행
작업을 *검증 가능한* 목표로 바꾸고 통과할 때까지 루프한다. "동작하게 만들어" 같은 약한 기준은 금지. 다단계는 `단계 → 검증방법`을 먼저 적는다.

---

## 한 줄 정의
사내 정산(SETTLEMENT) 지식 RAG + Spring AI **MCP 서버**. 벡터 DB 없이 **Redshift SQL**로 검색. 포트 8095, 루트 패키지 `com.hris.knowledgesearch`.

## 명령
| 목적 | 명령 |
|---|---|
| 로컬 실행(H2) | `./gradlew bootRun` |
| 테스트 | `./gradlew test` |
| 빌드 | `./gradlew clean build` |
| ETL 수동 실행 | `POST /etl/run` |

- 헬스 `/health` · Swagger `/swagger-ui.html` · H2 콘솔 `/h2-console`
- MCP 도구 3종: `search_knowledge` / `get_record` / `list_schema`
- REST: `POST /api/knowledge/search`, `GET /api/knowledge/{id}`, `GET /api/knowledge/schema`

## 작업 시 주의 (이 프로젝트 고유)
- **AWS 미연동**: Redshift·Spectrum·Glue·S3는 `application.yml`에 `# TODO(AWS)` 주석으로만 있고, 플래그(`search.spectrum.enabled`, `metadata.enabled`)는 기본 `false`. 실값은 사용자가 나중에 기재한다 — **임의로 켜지 마라.**
- **Spring AI MCP 버전**: 현재 `spring-ai-bom:1.0.0`. 코드에 `// TODO: verify` 표시. 업그레이드 시 `@Tool`/`MethodToolCallbackProvider` API 변동 확인.
- **`body`는 CLOB** → 키워드 매칭에 `lower()` 불가(현재 대소문자 구분 `contains`). 한글엔 무영향, Redshift 경로는 별도.
- **DDL**: H2는 `create-drop`. Redshift DDL은 Flyway가 아니라 별도 마이그레이션 스크립트(PRD §3.1).
- 검색 SQL은 자유 입력을 받지 않는다 — 허용 패턴 + 바인딩만(PRD §6).

## DDD 하네스 (opinionated-harness-template — 실용 헥사고날 + DDD)

> 코드 작성·수정 시 `.claude/hooks/harness.mjs`가 자동 검사하고(로컬 빠른 피드백), ArchUnit 이 CI 에서 구조 규칙을 정밀 강제한다. 상세는 `docs/HARNESS.md`. 카파시 4원칙과 같은 철학. 하네스는 **DEFAULT(verbatim) config** 를 쓴다 — 레이어는 패키지명으로 매핑된다.

### 패키지 구조 (루트 `com.hris.knowledgesearch`)
| 레이어 | 패키지 | 내용 |
|---|---|---|
| domain | `domain.knowledge` | `KnowledgeRecord`/`SearchLog`(`@AggregateRoot`+`@Entity`, 행위 메서드 보유) · 리포지토리 **포트**(`KnowledgeRecordRepository`/`SearchLogRepository`, 순수 인터페이스 — Spring 타입 없음) |
| application | `application.knowledge` | `KnowledgeSearchService`(`@Service`, 흐름 제어) · `dto.*`(요청/응답 DTO — 애플리케이션 계약) · `port.{MetadataResolvePort, MetadataResolveResult}`(아웃바운드 포트) |
| infrastructure | `infrastructure.*` | `persistence.knowledge.*RepositoryImpl`(포트 어댑터 = Spring Data `*JpaRepository` + QueryDSL) · `metadata.MetadataClient`(MetadataResolvePort 구현) · `etl.*`(배치/유틸) · `config.*`(QueryDsl/Cache/OpenApi/Mcp/JpaAuditing) |
| presentation | `presentation.*` | `knowledge.KnowledgeSearchController` · `mcp.KnowledgeSearchTools`(@Tool 인바운드 어댑터) · `etl.EtlController` (DTO 는 application.knowledge.dto) |
| (횡단) | `global.*` · `shared.ddd.*` | BaseEntity/ApiResponse/Exception/Health(레이어 외 횡단) · DDD 마커 5종 |

- **마커**: `shared.ddd.{AggregateRoot,AggregateInternal,ValueObject,DomainEvent,DomainService}`. AR 은 `KnowledgeRecord`/`SearchLog` 에만. VO/AggregateInternal 은 현재 미사용.
- **포트/어댑터(검증된 검색 보존)**: 도메인 포트는 앱/ETL 이 쓰는 메서드만 가진 순수 인터페이스(`search`/`findById`/`existsByContentHash`/`save`). QueryDSL 랭킹·토큰 OR·코드값 매칭·기간 토큰 skip 로직은 `KnowledgeRecordRepositoryImpl` 에 그대로 둔다(동작 동일).
- **차단(block) 규칙**: 도메인에 `@Service`/`@Transactional`/`@Setter`/`@Data`/public setter/`.now()`/`UUID.randomUUID()` 금지 · 빈약 엔티티 금지 · 필드주입(`@Autowired`) 금지(생성자 주입) · domain→바깥레이어 임포트 금지 · application→infra 임포트 금지(포트 사용) · 도메인 `*RepositoryImpl` 파일명 금지 · `./gradlew`만 사용.
- **application↛infra 경계**: metadata 호출은 `application.knowledge.port.MetadataResolvePort`(포트)에 의존, 구현은 `infrastructure.metadata.MetadataClient`.
- **ArchUnit**(`src/test/java/.../archunit`): DOMAIN_PURITY · REPOSITORY_IMPL_IN_INFRA · AGGREGATE_ACCESS · ID_REFERENCE_BETWEEN_AGGREGATES · VALUE_OBJECT_IMMUTABLE · NO_FIELD_INJECTION. `./gradlew test` 에서 함께 돈다. CI: `.github/workflows/ddd-archunit.yml`.
- **커맨드**: `/ddd-review`(변경분 감사) · `/ddd-fix`(점진 수정) · `/verify`(훅+테스트). 훅 실행에 Node.js 필요.
