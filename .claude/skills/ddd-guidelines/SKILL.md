---
name: ddd-guidelines
description: DDD(도메인 주도 설계) 준수 원칙. 도메인 모델·애그리거트·VO·리포지토리·응용 서비스·도메인 이벤트를 설계하거나 수정할 때, 또는 가드 훅이 도메인 순수성 위반을 보고했을 때 로드한다.
---

# DDD 준수 원칙

강제는 **2중**이다. 🔒 훅 차단: 도메인 import 순수성 · 필드주입 · 캡슐화(@Setter/@Data/setter) · DIP
(`*RepositoryImpl`, `*Repository`=interface) · VO/도메인이벤트 불변성. ⚠️ 훅 경고(휴리스틱): 애그리거트
경계 · ID 참조 · 빈약모델 · 최소 애그리거트 · 도메인서비스 무상태 · 팩토리. ✅ ArchUnit(CI): 레이어드·DIP·
애그리거트 접근·ID 참조·VO 불변·도메인@Entity 표시·AR 팩토리·CORE→GENERIC 의존·Request 입력 명명 +
**스프링 스테레오타입 금지·public setter 금지·비결정 API 금지·도메인서비스 무상태·Typed ID(자체 @Id surrogate 면제)·
@Autowired 전면 금지·AR 컬렉션 비노출·Command 불변**(훅 전용이던 도메인 클린코드 규칙을 ArchUnit으로 승격 → 직접
커밋도 CI 차단)를 *정밀* 차단(`src/test/java/**/archunit`, 총 20규칙 — 자체 application↛infra·원시 String 금지 2종 포함). 마커(`@AggregateRoot`·`@Subdomain`
등)를 도메인에 표시할 것. 서브도메인 분류는
이제 `@Subdomain(CORE|SUPPORTING|GENERIC)` + ArchUnit `CORE_NOT_DEPEND_ON_GENERIC`로, Command 입력 명명은
`REQUEST_INPUT_IS_COMMAND`로 **부분 강제**된다. **여전히 강제 불가(의미/런타임)** — 응용 로직 침투·단일TX-단일AR·
보편언어 명명·Tell-Don't-Ask — 는 아래 가이드를 **예방**으로 따르고 `ddd-reviewer` 감사로 보완한다(흉내내지 않음).

> **자바/스프링(실용 레이어드):** 이 템플릿 기본값은 도메인 엔티티의 JPA(`@Entity`)·Lombok 을 *허용*한다.
> 대신 도메인이 **프로젝트 infra 어댑터(S3/SQS/OAuth = `*.infra.*`)·application·외부 클라이언트**에
> 의존하는 것을 차단한다. CI 단의 권위 있는 게이트는 **ArchUnit** 으로 보완하길 권장(훅은 로컬 빠른 피드백).

## 1. Context & Architecture (전략적)
- 바운디드 컨텍스트 분리 및 패키지 격리.
- 서브도메인 유형(Core/Supporting/Generic) 분류, **Core 도메인 순수성 최우선 보호**. AR에 `@Subdomain(CORE|SUPPORTING|GENERIC)`를 표시하면 ArchUnit `CORE_NOT_DEPEND_ON_GENERIC`가 CORE→GENERIC 의존을 차단한다(부분 강제).
- 도메인 패키지 순수성: 외부 패키지 참조 금지(훅 + ArchUnit/의존성 검증으로 강제).
- 의존성 역전(DIP): **리포지토리 인터페이스만 도메인이 소유**, 구현은 infrastructure.
- 컨텍스트 맵 관계 명시, 경계 인터페이스(OHS/ACL)로 외부 연동.

## 2. Application Layer (응용 레이어)
- 응용 서비스는 **흐름 제어**(트랜잭션, 조회/저장, 보안)에 국한.
- 응용 서비스 내 **비즈니스 로직·검증식 침투 금지** → 도메인 모델에 위임.
- 보편 언어가 반영된 **Command 객체**로 외부 요청 수신. `..application..`의 `*Request` 명명은 ArchUnit `REQUEST_INPUT_IS_COMMAND`가 차단한다(Command 명명 강제).

## 3. Aggregate & Lifecycle (애그리거트)
- 애그리거트 루트(AR) 중심으로 외부 진입점 제한.
- **최소 크기** 설계(필수 외 속성은 VO 대체 또는 독립 애그리거트로 분리).
- 빈약한 도메인 모델 금지 — 행위·데이터 캡슐화.
- 내부 엔티티 생명주기/수정은 **AR 통해서만**(외부 직접 수정 금지).
- 애그리거트 간 참조는 **식별자(ID)로**(객체 직접 참조 금지).
- 단일 트랜잭션 = 단일 애그리거트 수정(타 애그리거트는 최종 일관성).
- 도메인 팩토리(정적 팩토리 메서드)로 생성 시점 무결성 보장.

## 4. Tactical Modeling & Communication (전술적)
- VO 로 원시 타입 집착(Primitive Obsession) 방지, **불변성** 유지.
- 보편 언어의 코드 투영, 비즈니스 행위 중심 명명.
- 상태 전이 규칙·제약 검증을 엔티티 내부에 캡슐화.
- ACL 로 외부 유입 데이터를 도메인 모델로 변환.
- 도메인 이벤트는 **불변·과거형 명명**(Command 와 대칭).
- 도메인 이벤트로 모듈 간 결합도 완화(Tell, Don't Ask).
- 도메인 서비스는 제한적으로(복수 애그리거트 연동·무상태 로직 분리).
