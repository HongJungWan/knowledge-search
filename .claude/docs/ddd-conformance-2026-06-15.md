# DDD 준수 매트릭스 — knowledge-search × metadata-ontology (2026-06-15)

7개 레이어 DDD 체크리스트(도메인 이벤트 2항목 제외) 전수 점검 결과. 강제수단: **AU**=ArchUnit(CI 게이트), **HK**=하네스 훅(로컬), **수동**=감사/코드리뷰. 두 프로젝트 동일 적용(차이는 비고).

## 1. Context & Architecture
| # | 원칙 | 상태 | 강제 | 증거 |
|---|---|---|---|---|
|1.1|BC 분리·패키지 격리|✅|수동|두 BC(ks/mo), `domain/application/infrastructure/presentation` 레이어. context-map.md|
|1.2|서브도메인 분류 + Core 순수성|✅|AU `CORE_NOT_DEPEND_ON_GENERIC`|`@Subdomain(CORE/SUPPORTING/GENERIC)` 부착|
|1.3|도메인 패키지 순수성|✅|AU `DOMAIN_PURITY`+`DOMAIN_NO_SPRING_STEREOTYPES`(신규)|도메인→외부레이어/Spring 스테레오타입 금지 CI 강제|
|1.4|DIP 리포지토리 격리(인터페이스만 도메인)|✅|AU `REPOSITORY_IMPL_IN_INFRA`+`APPLICATION_NOT_DEPEND_ON_INFRASTRUCTURE`|포트는 도메인, `*RepositoryImpl`은 infra|
|1.5|컨텍스트맵 + OHS/ACL 경계|✅|수동|ks ACL: `MetadataResolvePort`·`SchemaCatalogPort`·`SettlementSourceAcl`(+ B1 `DictionaryCsvSource`). mo OHS: `@PublishedLanguage`(`ResolveResponse`·`SchemaCatalogResponse`) + `PhysicalCatalogSourcePort`(ACL seam)|

## 2. Application Layer
| # | 원칙 | 상태 | 강제 | 증거 |
|---|---|---|---|---|
|2.1|응용=흐름제어 국한|✅|수동|서비스는 포트 호출·DTO 매핑·트랜잭션만|
|2.2|응용에 비즈니스/검증 침투 금지|✅|AU `DOMAIN_PURITY`|B1: `DictionaryImportService` CSV파싱/검증/기본값 → ACL+도메인 위임. B2: 평가 판정/해석/매칭 규칙도 도메인 서비스로 추출 완료 — ks `SearchQualityGate`·`RetrievalInterpretation`(+값객체 `StratumScore`/`ArmScore`), mo `MappingComparator`. 응용 서비스는 DTO→원시값 매핑·요약 문자열·집계 루프 등 흐름 제어만. 도메인 서비스는 원시값/도메인 값객체만 입력받아 `DOMAIN_PURITY` 통과(평가 DTO 미참조).|
|2.3|Command 객체 입력|✅|AU `REQUEST_INPUT_IS_COMMAND`|`@RequestBody`는 `*Command`(ks) / `*Request` 잔존 금지(mo)|

## 3. Aggregate & Lifecycle
| # | 원칙 | 상태 | 강제 | 증거 |
|---|---|---|---|---|
|3.1|애그리거트 경계·AR 진입점|✅|AU `AGGREGATE_ACCESS`|`@AggregateRoot`/`@AggregateInternal`|
|3.2|최소 애그리거트(VO/분리)|✅|수동|Synonym/CodeValue 독립 AR(ADR §1). 값은 VO化|
|3.3|빈약 모델 금지·캡슐화|✅|HK anemic + AU `DOMAIN_NO_SETTERS`(신규)|AR 행위 메서드 보유, public setter 금지(@Data/@Setter 차단)|
|3.4|AR 통한 내부 생명주기 제어|✅|AU `DOMAIN_NO_SETTERS`|외부 직접 수정 불가(setter 없음), `update()`/`changeStatus()` 경유|
|3.5|ID 참조 강제|✅|AU `ID_REFERENCE_BETWEEN_AGGREGATES`|교차참조 타입드 ID(mo: TermId 등) / 객체참조 금지|
|3.6|단일 트랜잭션 단일 애그리거트|✅(문서화 예외 포함)|수동|C: `DictionaryImportService` → `DictionaryRowImporter` 행단위 트랜잭션. **허용 패턴**: `KnowledgeSearchService.search/getRecord`(KnowledgeRecord 읽기 + SearchLog 감사쓰기 — 비핵심 side-effect), ETL 청크·`CatalogSyncService` 루프(동일 애그리거트 타입 벌크)|
|3.7|도메인 팩토리 무결성|✅|AU `AGGREGATE_ROOT_HAS_FACTORY`+`DOMAIN_NO_NONDETERMINISM`(신규)|정적 팩토리(`create`/`forIngestion`/`record`), 도메인 내 `.now()`/`randomUUID()` 금지(시계·ID 주입)|

## 4. Tactical Modeling (도메인 이벤트 제외)
| # | 원칙 | 상태 | 강제 | 증거 |
|---|---|---|---|---|
|4.1|VO 원시집착 방지·불변|✅|AU `VALUE_OBJECT_IMMUTABLE`+`DOMAIN_ENTITY_NO_RAW_STRING`(신규)|모든 값-필드 `@Embeddable` VO(+ ks 값VO 11종, mo 값VO 17종+Priority), 식별자 타입드 ID(mo). 엔티티 raw String 금지로 회귀 차단. (ks PK Long: IDENTITY+converter JPA 제약으로 의도적 예외)|
|4.2|UL 코드 투영·행위 명명|✅|수동|`belongsTo`·`hasCodeValue`·`changeStatus`·`matchesKeyword` 등 — `process/handle/doX` 안티패턴 없음|
|4.3|상태전이 규칙 엔티티 캡슐화|✅|수동|`TermStatus.canTransitionTo` + `Term.changeStatus`(위반 시 `IllegalStateException`)|
|4.4|ACL 외부데이터 변환|✅|수동|ks `SettlementSourceAclAdapter`·`MetadataClient`·`DictionaryCsvSourceAdapter`(신규) / mo `PhysicalCatalogSourceAdapter`|
|4.7|도메인 서비스 제한적·무상태|✅|AU `DOMAIN_SERVICE_STATELESS`(신규)|`@DomainService`(ks `QueryRouter`; mo `Normalization/Expansion/SqlPatternService`) — final 필드만, `DomainServiceConfig` @Bean 등록|

## 요약
- **CI(ArchUnit) 강제 항목**: 16개(Track A 로 5개 추가 — Spring 스테레오타입·setter·비결정성·도메인서비스 무상태·엔티티 raw String).
- **코드 변경 트랙**: A(ArchUnit 확대)·B1(DictionaryImport ACL)·B2(평가 규칙 도메인 서비스 추출)·C(행단위 트랜잭션)·D(OHS 마커)·E(Priority VO). 모두 `./gradlew clean build` GREEN.
- **문서화된 의도적 예외**: (3.6) 감사로그/벌크 트랜잭션 패턴. (4.1) ks IDENTITY PK Long(JPA converter 제약). (제외) 도메인 이벤트(사용자 제외).
- **결론**: 23개 적용 항목 전부 충족(✅) — 위반 0, 일부는 근거와 함께 문서화된 예외.
