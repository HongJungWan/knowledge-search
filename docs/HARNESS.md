# 하네스 엔지니어링 가이드 (Java/Spring)

AI 에이전트가 짠 Java/Spring 코드를 DDD 원칙에 맞게 잡아주는 가드레일이 어떻게 동작하는지 정리한 문서예요. 대상 스택은 Java 21 · Spring Boot · Gradle(wrapper) · Flyway 예요.

<br>

## 이 가드레일이 하는 일

다섯 개의 레버를 조합해서 에이전트의 동작을 제약해요. 그중 **훅(Hooks)이 가장 큰 레버**예요. 사람이 매번 리뷰로 잡지 않아도, 에이전트가 코드를 쓰는 그 자리에서 DDD 위반을 짚어줘요.

| 레버 | 위치 | 역할 |
|---|---|---|
| 시스템 프롬프트 | `CLAUDE.md` (60줄 이하) | 세션 시작 시 주입되는 최상위 제약 — 카파시 4원칙 · 스택 · 규칙 · 명령 |
| 스킬 (필요할 때만 로드) | `.claude/skills/*/SKILL.md` | 도메인 지식을 모듈로 떼어 둠. 컨텍스트를 깨끗하게 유지하려고 필요한 순간에만 꺼내 봐요 |
| 서브에이전트 | `.claude/agents/*.md` | 무거운 검토는 단기 에이전트에 맡기고 결론만 받아요. 메인 컨텍스트가 오염되지 않게 막는 방화벽 |
| **훅 (자동 검증)** | `.claude/hooks/` | **에이전트가 쓴 코드를 읽고 DDD 규칙으로 자동 검사. 가장 큰 성능 레버.** |
| 마커 어노테이션 | `ddd-markers/` | `@AggregateRoot` · `@AggregateInternal` · `@ValueObject` · `@DomainEvent` · `@DomainService` — 훅(이름 기준)과 ArchUnit(클래스 기준)이 같은 마커를 함께 봐요 |

여기에 더해 **슬래시 커맨드**(`.claude/commands/`)와 **MCP 서버**(`.mcp.json`)를 조합해 흐름과 도구를 엮어요. 자세한 건 아래 해당 섹션에 있어요.

<br>

## 컨텍스트 엔지니어링과 하네스 엔지니어링

위 레버들은 사실 두 종류의 문제를 풀어요. 섞어 쓰지 말고 경계를 의식하면 설계가 또렷해져요.

- **컨텍스트 엔지니어링 — "모델이 무엇을 보는가"**: 한정된 컨텍스트 창에 옳은 정보를, 옳은 양만큼, 옳은 형식으로, 옳은 시점에 넣는 일이에요(카파시). 시스템 프롬프트·스킬·서브에이전트가 여기에 속해요.
- **하네스 엔지니어링 — "모델이 무엇을 할 수 있고, 무엇이 검증하는가"**: 에이전트를 둘러싼 환경(도구 접근·차단·게이트)을 설계해 코드가 옳게 나오도록 강제하는 일이에요. 훅·MCP·ArchUnit이 여기에 속해요.

| 레버 | 축 | 한 일 |
|---|---|---|
| 시스템 프롬프트 · 스킬 · 서브에이전트 | 컨텍스트 | 모델이 보는 것을 고른다 |
| 훅 · MCP · ArchUnit | 하네스 | 모델이 할 수 있는 것과 검증을 정한다 |
| 슬래시 커맨드 | 둘을 잇는 자리 | 컨텍스트(프롬프트)와 하네스(도구·게이트)를 한 워크플로로 엮는다 |

> 같은 DDD 규칙도 컨텍스트 축(스킬·리뷰로 *예방*)과 하네스 축(훅·ArchUnit으로 *강제*)에서 동시에 다뤄요. 컨텍스트는 모델을 잘 안내하고, 하네스는 안내가 빗나가도 막아요.

<br>

## 훅이 언제 어떻게 도는가

훅은 네 가지 시점에 돌아요. 각 시점이 잡는 위반이 달라요.

**파일 수정 직전 — `protect`**

Flyway 마이그레이션(`V#__*.sql`)을 고치거나 지우려 하면 실행 전에 막아요. 새 버전을 추가하라는 신호를 돌려줘요.

**Bash 실행 직전 — `bash`**

전역 `mvn`이나 `gradle` 실행을 막고 `./gradlew`를 쓰라고 알려줘요. 빌드 도구 불일치로 생기는 환경 차이를 막아요.

**파일 수정 직후 — `guard` (가장 자주 도는 훅)**

수정이 끝난 *완성 파일*과 그 파일이 참조하는 타입(import-follow)까지 읽어서 DDD 규칙으로 검사해요. 결과는 두 갈래로 나와요.

- 🔒 **차단(exit 2)** — 그 자리에서 고쳐야 해요.
    - 도메인이 인프라·외부 레이어를 import
    - 도메인 클래스에 Spring 스테레오타입(`@Service`·`@Component`·`@Repository`·`@Controller`·`@RestController`)·`@Transactional` 부착
    - 도메인이 `ApplicationEventPublisher`·`EventBus` 같은 Spring 이벤트 발행자 import
    - 도메인이 `LocalDateTime.now()`·`Instant.now()`·`System.currentTimeMillis()` 같은 시간 API 직접 호출
    - 도메인이 `new Random()`·`UUID.randomUUID()` 같은 난수/UUID 직접 호출
    - 응용 레이어가 인프라 구체(`JdbcTemplate`·`RestTemplate`·AWS SDK 등) 직접 import
    - 필드 주입 (`@Autowired` 필드)
    - 도메인 캡슐화 위반 (`@Setter` · `@Data` · public setter)
    - DIP 위반 (`*RepositoryImpl`이 도메인에, `*Repository`가 클래스로)
    - 값 객체 · 이벤트의 가변 노출
- ⚠️ **경고(exit 0)** — 알려만 주고 막지는 않아요. 정규식 휴리스틱이라 오탐 여지가 있어서 기본은 비차단이에요.
    - 애그리거트 경계 침범 · ID 참조 · 빈약한 모델 · 최소 애그리거트 · 도메인 서비스 무상태 · 팩토리 · 이벤트 이름 과거형

**완료 선언 직전 — `checklist`**

작업을 끝내겠다고 신호하기 전에 자가 점검 체크리스트를 한 번 띄워요. 요구사항을 빠뜨리지 않았는지 에이전트가 스스로 대조해요.

위반이 잡히면 훅은 `exit 2 + stderr`로 신호하고, Claude Code가 그 메시지를 에이전트에게 보여줘요. 에이전트는 같은 흐름에서 바로 코드를 고쳐요. 모든 로직은 `harness.mjs`(Node, 의존성 0) 한 파일에 있어요. Node만 있으면 Gradle을 안 쓰는 환경에서도 돌아요.

<br>

## 왜 파일 수정 직후(PostToolUse)에 잡는가

`Edit` 도구는 수정 조각(`new_string`)만 훅에 넘겨요. 그 조각만 봐서는 파일 전체 import를 알 수 없어 도메인 순수성 같은 규칙을 정확히 검사할 수 없어요. 그래서 DDD 가드는 수정이 끝난 *완성 파일*을 직접 읽는 PostToolUse로 둬요. 반대로 마이그레이션 보호나 명령 차단은 입력만 봐도 판단되므로 PreToolUse면 충분해요.

훅과 ArchUnit은 역할이 달라요. 훅은 로컬 에이전트 루프에서 빠르게 도는 1차 강제예요(휴리스틱이라 경고는 오탐 허용). 사람과 CI까지 포함한 권위 있는 최종 게이트는 ArchUnit이 받쳐줘요. 둘의 분담은 [`ARCHUNIT.md`](ARCHUNIT.md)에 정리해 뒀어요.

<br>

## 세 단계로 가져다 쓰기

```bash
# 1. 대상 프로젝트 루트에 복사 (.claude 에 hooks/skills/agents/commands 포함)
cp -r opinionated-harness-template/.claude   <your-project>/.claude
cp    opinionated-harness-template/CLAUDE.md  <your-project>/CLAUDE.md
cp    opinionated-harness-template/.mcp.json  <your-project>/.mcp.json   # MCP 쓸 때만(opt-in)
```

2. `.claude/hooks/harness.config.json`에서 **이 다섯 가지만** 손봐요.
    - `layers` · `forbiddenImports` — 프로젝트 패키지 구조에 맞춰요. 순서 주의: `presentation`(컨트롤러)을 `domain` 앞에 둬서 도메인 판정에서 빠지게 해요.
    - `forbiddenContentPatterns` — 필드 주입 같은 콘텐츠 정규식 규칙.
    - `protectedPaths` — Flyway/Liquibase 마이그레이션 경로.
    - `forbiddenCommands` · `commandRule` — 빌드 도구가 Gradle이 아니면 여기서 바꿔요.
    - `importPatterns` — Kotlin 같은 다른 JVM 언어를 추가할 때만.

3. `CLAUDE.md`의 `<...>` 스택/버전 플레이스홀더를 채워요.

끝이에요. 다음 `Edit`/`Write`부터 훅이 자동으로 돌아요. 훅이 진짜로 도는지 확인하려면 `./scripts/verify-harness.sh`를 한 번 돌려봐요. 마지막 줄에 `✅ 하네스 훅 검증 완료`가 뜨면 가드레일이 깔린 거예요.

<br>

## 22 원칙 어디까지 잡아주나

훅(로컬, 빠름, 휴리스틱 허용)과 ArchUnit(CI, 정밀, 오탐 없음)이 2중 방어를 구성해요. 겹치는 구조 규칙은 훅에선 경고로 두고 ArchUnit에서 차단해서, 오탐 마찰 없이 커버리지를 넓혀요.

| 강제 수준 | 원칙 # |
|---|---|
| 🔒 훅이 차단 | 도메인 순수성(#3): import 차단 · Spring 스테레오타입/`@Transactional` · EventPublisher · 시간/난수 API · DIP(#4): 응용→인프라 import · 캡슐화·setter(#11·#18) · 값 객체·이벤트 불변(#16·#20) · 필드 주입 |
| ⚠️ 훅이 경고 | 애그리거트 경계(#9) · ID 참조(#13) · 빈약한 모델(#11) · 최소 애그리거트(#10) · 도메인 서비스(#22) · 팩토리(#15) · BC 격리(#1) · 이벤트 이름(#20) |
| ✅ ArchUnit 정밀 | 레이어 의존·도메인 순수성(#3) · DIP(#4) · 애그리거트 접근(#9·#12) · ID 참조(#13) · 값 객체 불변(#16) |
| 📝 자동 강제 불가 → 스킬·리뷰 | 서브도메인 분류(#2) · 응용 로직 침투(#6·#7) · Command(#8) · 단일 TX·단일 AR(#14) · 보편 언어 명명(#17) · Tell-Don't-Ask(#21) |

- **실용 레이어드**가 기본이에요. 도메인 엔티티에 JPA(`@Entity`)·`@Getter`·Lombok은 허용하고, 가변을 여는 `@Setter`·`@Data`만 막아요. 순수 헥사고날로 더 조이려면 `forbiddenImports.domain`에 `jakarta.persistence` · `org.springframework` · `lombok`을 추가하면 돼요.
- **자동 강제 불가 6개**는 의미·런타임 분석이 필요해서 흉내내지 않아요. `ddd-guidelines`(예방) + `ddd-reviewer`(감사) + 완료 직전 체크리스트로만 다뤄요. 자동화한 척 거짓 자신감을 주지 않으려는 의도예요.
- **경고 규칙은 정규식 휴리스틱**이라 오탐 여지가 있어요. 그래서 기본은 비차단이에요. 프로젝트가 단단해지면 `harness.config.json`의 `checks`에서 `block`/`warn`/`off`로 조정할 수 있어요.

<br>

## 슬래시 커맨드로 흐름 엮기

`.claude/commands/`에 자주 쓰는 흐름을 커맨드로 묶어 뒀어요. 코드를 찍어내는 생성기가 아니라, 이미 있는 레버(서브에이전트·스킬·훅·테스트)를 엮는 오케스트레이터예요. 권한은 각 커맨드의 frontmatter `allowed-tools`로 좁혀요 — 예를 들어 `/ddd-review`는 `Bash(git diff:*)`와 `Task`만 써요.

| 커맨드 | 하는 일 | 카파시 원칙 |
|---|---|---|
| `/ddd-review [기준]` | 바뀐 파일을 `ddd-reviewer` 서브에이전트로 감사하고 위반 목록만 받아와요 | ④ 실행 검증 + 방화벽 |
| `/verify` | 훅 셀프테스트 → `./gradlew test` → ArchUnit을 한 번에 돌려요 | ③ 디버깅 · ④ 검증 |
| `/ddd-fix [리포트]` | `ddd-guidelines`를 펴고 위반을 최소 단위로 점진 수정·재검증해요 | ① 점진 · ③ 디버깅 |

흐름은 보통 `/ddd-review`(감사) → `/ddd-fix`(수정) → `/verify`(검증)로 이어져요.

<br>

## MCP 서버 (`.mcp.json`, opt-in)

에이전트에게 외부 도구·데이터 접근을 주는 하네스 레버예요. 기본으로 `postgres`(읽기 전용 스키마 조회)를 넣어, `db-migration`·`jpa-persistence` 작업 때 실제 스키마를 보고 판단하게 했어요.

- 접속 문자열은 `${POSTGRES_URL}` 환경변수로 주입해요. **DB 자격증명 평문 커밋은 금지예요.**
- 프로젝트 `.mcp.json`의 서버는 처음 쓸 때 승인을 물어봐요(본질적으로 opt-in). 자동으로 켜려면 `.claude/settings.local.json`의 `enabledMcpjsonServers`에 적어요(로컬·비커밋).
- 안 쓰면 `.mcp.json`을 복사 안 하면 돼요. 훅은 Node만으로 도니까 MCP 없이도 완전해요.

<br>

## 오픈소스 하네스에서 가져온 것

공개 하네스의 검증된 패턴을 Java/Spring·DDD 맥락에 맞게 가져왔어요.

- **superpowers** (Claude Code 플러그인) — 스킬 점진 공개와 커맨드 주도 워크플로. 이 템플릿의 `.claude/skills/*`(필요할 때 로드)와 `.claude/commands/*`(`/ddd-review`→`/ddd-fix`→`/verify` 흐름)가 대응해요.
- **oh-my-codex** — 에이전트 환경을 복사-붙여넣기로 셋업하는 배포 모델. 이 템플릿의 "세 단계로 가져다 쓰기"와 `harness.config.json` 단일 설정 지점이 대응해요.

차이는, 두 프로젝트가 범용 워크플로에 집중하는 반면 이 템플릿은 **DDD 규칙의 결정론적 강제**(훅 + ArchUnit + 마커)에 특화했다는 점이에요. 컨텍스트 레버는 가져오고, 하네스 레버(게이트)는 도메인에 맞게 새로 짰어요.
