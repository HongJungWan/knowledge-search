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
