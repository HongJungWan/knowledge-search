# ArchUnit 게이트 (CI 정밀 강제)

훅이 보지 못하는 *여러 파일에 걸친 구조 위반*을 컴파일된 클래스 그래프 전체로 잡아내는 CI 게이트다. 훅이 빠른 1차 강제(휴리스틱·로컬·단일 파일)라면, ArchUnit은 클래스 그래프 단위의 권위 있는 최종 게이트(정확·CI)다. 훅이 잡는 룰 목록은 [`HARNESS.md`](HARNESS.md) 참조.

## 규칙 20가지

규칙 정의는 `src/test/java/com/hris/knowledgesearch/archunit/DddRules.java` 한 파일에 모여 있고, `DddArchitectureTest`(`@AnalyzeClasses(packages = "com.hris.knowledgesearch")`)가 프로덕션 코드에 적용한다. opinionated-harness-template 18규칙 + 본 레포 자체 2규칙(`DDD_APP_NOT_DEPEND_ON_INFRA`, `DDD_NO_PRIMITIVE_OBSESSION`).

| 코드 | 잡는 것 | 출처 |
|---|---|---|
| `DDD_DOMAIN_PURITY` | 도메인이 application·infrastructure·presentation에 의존하면 차단 | 템플릿 |
| `DDD_DIP` | `*RepositoryImpl`이 infrastructure가 아닌 곳에 있으면 차단 | 템플릿 |
| `DDD_AGGREGATE_ACCESS` | `@AggregateInternal`을 같은 애그리거트(패키지) 밖에서 접근하면 차단 (현재 미사용 → vacuous 허용) | 템플릿 |
| `DDD_ID_REFERENCE` | 애그리거트 루트 필드가 다른 루트를 객체로 직접 참조하면 차단 (ID로) | 템플릿 |
| `DDD_VO_IMMUTABLE` | `@ValueObject`의 필드가 `final`이 아니면 차단 | 템플릿 |
| `DDD_NO_FIELD_INJECTION` | 필드 주입(`@Autowired` 필드) 차단 | 템플릿 |
| `DDD_DOMAIN_ENTITY_MARKED` | 도메인 `@Entity`가 `@AggregateRoot`/`@AggregateInternal` 미표시면 차단 (문자열 매칭) | 템플릿 |
| `DDD_AGGREGATE_ROOT_HAS_FACTORY` | `@AggregateRoot`에 자기 타입 반환 `public static` 팩토리 없으면 차단 | 템플릿 |
| `DDD_CORE_NOT_DEPEND_ON_GENERIC` | `@Subdomain(CORE)`가 `@Subdomain(GENERIC)`에 의존하면 차단 (ks엔 GENERIC 부재 → vacuous) | 템플릿 |
| `DDD_REQUEST_INPUT_IS_COMMAND` | `@RestController`의 `@RequestBody` 파라미터가 `..command..`의 `*Command` 타입이 아니면 차단 (**템플릿의 `*Request` 명명 금지보다 강한 로컬 변형** — 실제 컨트롤러 입력 검사) | 템플릿(강화) |
| `DDD_NO_SPRING_IN_DOMAIN` | 도메인에 스프링 스테레오타입/`@Transactional` 차단 (문자열 매칭) | 템플릿 |
| `DDD_NO_PUBLIC_SETTER` | 도메인 public setter(`set[A-Z]*`) 차단 — Lombok 생성 setter도 바이트코드로 포착 | 템플릿 |
| `DDD_NO_NONDETERMINISTIC_API` | 도메인에서 시간(`*.now()`·`System.currentTimeMillis/nanoTime`)·난수(`UUID.randomUUID()`·`Math.random()`·`ThreadLocalRandom`·`new Random()`·`SecureRandom`) 직접 호출 차단 | 템플릿 |
| `DDD_DOMAIN_SERVICE_STATELESS` | `@DomainService`의 인스턴스 필드가 `final` 아니면 차단 | 템플릿 |
| `DDD_TYPED_ID` | `@AggregateRoot`/`@AggregateInternal`의 **다른 애그리거트 참조 식별자**(`*Id`)가 원시 타입이면 차단. **자체 surrogate 키**(`id` 또는 JPA `@Id`/`@EmbeddedId`)는 면제 — ks의 `KnowledgeRecord.id`(`Long`)는 면제 대상 | 템플릿(PR #13) |
| `DDD_NO_AUTOWIRED_IN_DOMAIN` | 도메인 멤버(필드·생성자·메서드) 어디든 `@Autowired` 차단 | 템플릿(PR #13) |
| `DDD_NO_EXPOSED_COLLECTION` | `@AggregateRoot`의 public 인스턴스 메서드가 내부 컬렉션을 raw `List`·`Set`·`Map`으로 반환하면 차단 (불변 뷰/복사/Stream으로 노출) | 템플릿(PR #13) |
| `DDD_COMMAND_IMMUTABLE` | `..application..`의 `*Command`가 불변 아니면(record 아님 + setter/비-final 필드) 차단 | 템플릿(PR #13) |
| `DDD_APP_NOT_DEPEND_ON_INFRA` | application이 infrastructure에 의존하면 차단 (포트 사용 — DIP) | **자체** |
| `DDD_NO_PRIMITIVE_OBSESSION` | 도메인 `@Entity`의 (비static) `String` 필드 차단 → `@ValueObject`로 포장 | **자체** |

> 마커 어노테이션은 `src/main/java/com/hris/knowledgesearch/shared/ddd/`에 있다(`@AggregateRoot`·`@AggregateInternal`·`@ValueObject`·`@DomainService`·`@DomainEvent`·`@Subdomain`·`@SubdomainType`). 훅과 ArchUnit이 같은 마커를 본다.
>
> 매칭 타입이 실재하는 규칙은 `allowEmptyShould(false)`로 두어 0매칭(침묵 통과)을 실패로 잡는다 — 즉 통과 = 실제 평가됨. 매칭이 비어 있을 수 있는 규칙(`AGGREGATE_ACCESS`·`CORE_NOT_DEPEND_ON_GENERIC`·`DOMAIN_SERVICE_STATELESS`·`DOMAIN_ENTITY_NO_RAW_STRING`)만 `allowEmptyShould(true)`.

## 돌려보기

```bash
./gradlew test --tests 'com.hris.knowledgesearch.archunit.*'   # 20규칙만
./gradlew test                                                 # 단위 + ArchUnit 전체
```

음성(negative) 검증은 별도 `baddomain` 픽스처 대신 `scripts/verify-harness.sh`가 담당한다(실소스 전수 스캔 0차단 + 합성 위반 차단 확인). CI는 `.github/workflows/ddd-archunit.yml`의 두 잡(hook-selftest + archunit)으로 강제하며, 브랜치 보호의 required check로 묶는다.

## 기존 코드에 위반이 많을 땐 (baseline 래칫)

위반이 잔뜩 나오는 레거시라면 `FreezingArchRule.freeze(...)`로 기존 위반을 동결하고 *새 위반만* 차단한다.

```java
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;
@ArchTest static final ArchRule domainPurity = freeze(DddRules.DOMAIN_PURITY);
```

최초 1회 실행으로 `archunit_store/` baseline이 생성된다. 커밋해두면 이후 새 위반만 RED. (본 레포는 현재 전 규칙 GREEN이라 freeze 미사용.)
