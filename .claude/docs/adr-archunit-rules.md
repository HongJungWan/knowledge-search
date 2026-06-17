# ADR — ArchUnit 강제 범위 확대 (DDD 체크리스트 CI 게이트)

> 상태: Accepted (2026-06-15). 컨텍스트: DDD 준수 체크리스트 전수 점검 — 하네스(로컬 훅)만 강제하던 항목을 ArchUnit(CI 권위 게이트)으로도 승격.

## 배경
기존 ArchUnit 11룰은 레이어·DIP·애그리거트·VO 불변성 등을 강제했으나, 일부 체크리스트 항목(도메인의 Spring 스테레오타입/세터/시계·난수 금지, 도메인 서비스 무상태)은 `.claude/hooks/harness.mjs`(로컬 휴리스틱)만 강제했다. CI(`./gradlew test`)에서도 동일하게 막기 위해 ArchUnit 룰로 승격한다.

## 추가된 룰 (양 프로젝트 isostructural)
| 룰 | 강제 내용 | 근거(체크리스트) |
|---|---|---|
| `DOMAIN_NO_SPRING_STEREOTYPES` | 도메인 클래스에 `@Service/@Component/@Repository/@Controller/@RestController/@Transactional/@Autowired` 금지(어노테이션 fqcn 검사) | 도메인 패키지 순수성 |
| `DOMAIN_NO_SETTERS` | 도메인에 public `setX(단일인자)` 메서드 금지 | 캡슐화 / AR 통한 수정 |
| `DOMAIN_NO_NONDETERMINISM` | 도메인 코드유닛이 `java.time.*.now()`·`UUID.randomUUID()`·`new Random()` 직접 호출 금지(바이트코드 호출 그래프 검사) | 결정성·팩토리 무결성(시계/ID 주입) |
| `DOMAIN_SERVICE_STATELESS` | `@DomainService` 의 비static 필드는 `final` | 도메인 서비스 무상태 |
| `DOMAIN_ENTITY_NO_RAW_STRING` | 도메인 `@Entity` 의 비static `String` 필드 금지(→ `@Embedded` 값 객체) | 원시 집착 방지 / VO 회귀 방지 고정 |

## 설계 근거
- **Lombok `@Data`/`@Setter` 는 SOURCE 보존**이라 ArchUnit 바이트코드 분석에 보이지 않는다 → 그 *효과*(생성된 public setX 메서드)를 `DOMAIN_NO_SETTERS` 로 관측·차단(손수 작성 세터도 함께 차단).
- `DOMAIN_ENTITY_NO_RAW_STRING` 은 직전 VO 전면 적용을 **영구 고정**하는 회귀 가드 — 향후 누가 엔티티에 raw String 필드를 추가하면 CI 실패. (Instant/enum/@Id/int 는 대상 외 — 원시 집착의 핵심인 String 만 타깃.)
- `#15 빈약 모델(anemic)` 은 휴리스틱·오탐 위험이 커 ArchUnit 룰로 만들지 않고 하네스 전담 유지.

## 결과
- 두 프로젝트 모두 신규 룰 포함 `./gradlew test` GREEN(기존 코드가 이미 하네스 0차단이라 부합). 25개 체크리스트 중 CI 강제 범위가 ~11 → 16 항목으로 확대.

---

## 추가 (2026-06-17) — opinionated-harness-template PR #13 동기화 (16 → 20)

> 상태: Accepted. 컨텍스트: 템플릿이 ArchUnit 룰을 10→18로 확대(PR #13)하고 휴리스틱 기본을 all-block 으로 문서 동기화. 본 레포도 18룰 기반으로 리싱크하되 자체 2룰과 더 강한 로컬 변형은 유지.

### 변경
- **신규 4룰 추가**(템플릿 PR #13):
  | 룰 | 강제 내용 |
  |---|---|
  | `AGGREGATE_ID_FIELD_IS_TYPED` | AR/AggregateInternal 의 다른 애그리거트 참조 식별자(`*Id`)는 전용 VO. 자체 surrogate(`id`/`@Id`/`@EmbeddedId`)는 면제 |
  | `NO_AUTOWIRED_IN_DOMAIN` | 도메인 멤버(필드/생성자/메서드) 전반 `@Autowired` 금지 |
  | `AGGREGATE_NO_EXPOSED_MUTABLE_COLLECTION` | AR public 인스턴스 메서드가 raw `List/Set/Map` 반환 금지 |
  | `COMMAND_IS_IMMUTABLE` | `..application..` 의 `*Command` 는 record 또는 불변(setter 無·필드 final) |
- **명명/구현 리싱크**: 템플릿 정본 명명 채택 — `DOMAIN_NO_SPRING_STEREOTYPES`→`NO_SPRING_STEREOTYPES_IN_DOMAIN`, `DOMAIN_NO_SETTERS`→`DOMAIN_NO_PUBLIC_SETTER`, `DOMAIN_NO_NONDETERMINISM`→`DOMAIN_NO_NONDETERMINISTIC_API`. 비결정성 탐지는 `Math.random`·`ThreadLocalRandom`·`SecureRandom`·`System.currentTimeMillis/nanoTime` 까지 확장.
- **더 강한 로컬 변형 유지**: `REQUEST_INPUT_IS_COMMAND` 는 템플릿의 application `*Request` 명명 금지 대신 ks 기존의 **@RestController @RequestBody 파라미터 타입 = `..command.. *Command`** 검사를 유지(실제 컨트롤러 입력을 검증해 더 엄격).
- **자체 2룰 유지**: `APPLICATION_NOT_DEPEND_ON_INFRASTRUCTURE`·`DOMAIN_ENTITY_NO_RAW_STRING`(템플릿엔 없음).
- **게이트 약화 금지**: 템플릿 드롭인의 `allowEmptyShould(true)` 로 되돌리지 않고, 매칭 타입이 실재하는 룰은 `false` 유지(침묵 통과 방지). 매칭이 비어 있을 수 있는 `AGGREGATE_ACCESS`·`CORE_NOT_DEPEND_ON_GENERIC`(GENERIC 부재)·`DOMAIN_SERVICE_STATELESS`·`DOMAIN_ENTITY_NO_RAW_STRING` 만 `true`.

### 결과
- `./gradlew test` 20룰 GREEN, 신규 4룰 모두 비-vacuous 평가(프로덕션 코드 수정 0건 — query-target 필드가 이미 `@ValueObject`, surrogate `id` 는 Typed-ID 면제, AR 컬렉션 노출 0, 전 Command 가 record). CI 강제 범위 16 → 20.
