---
description: guard/ArchUnit/ddd-review가 보고한 DDD 위반을 ddd-guidelines에 따라 점진적으로 수정
argument-hint: "[위반 리포트 또는 대상 파일 · 비우면 직전 컨텍스트의 위반 사용]"
allowed-tools: Bash(./scripts/verify-harness.sh), Bash(./gradlew:*), Bash(cd archunit*), Edit, Read, Skill
---

가드 훅·ArchUnit·`/ddd-review` 가 보고한 DDD 위반을 수정한다. **추측 수정 금지** —
위반 메시지와 해당 코드를 끝까지 읽고 근본 원인을 파악한 뒤 고친다.

## 절차

1. **가이드 로드** — `ddd-guidelines` 스킬을 로드해 수정 기준을 컨텍스트에 둔다.

2. **위반 수집** — `$ARGUMENTS` 가 있으면 그 리포트/파일을 대상으로, 없으면 직전 대화의
   guard/ArchUnit/`/ddd-review` 위반 목록을 대상으로 삼는다. 대상이 불명확하면 사용자에게 묻는다.

3. **점진적 수정 (카파시 ①)** — 위반을 한 번에 하나씩, 컴파일·테스트 가능한 **최소 단위**로 고친다:
   - 도메인 순수성/DIP 위반 → 의존을 인터페이스(도메인 소유)로 역전, 구현은 infrastructure 로 이동.
   - 캡슐화/setter/필드주입 → 의미 있는 행위 메서드 + 생성자 주입으로 교체.
   - VO/이벤트 가변 → record 또는 final 필드 + 과거형 명명으로 교정.
   - 빈약 모델 → 흩어진 로직을 엔티티/VO 안으로 캡슐화.
   임의 확장·보일러플레이트 생성 금지. 기존 코드 스타일을 따른다.

4. **재검증 (카파시 ④)** — 각 수정 후 `./scripts/verify-harness.sh` 와
   `./gradlew test --tests '*archunit*'` 로 위반이 사라졌는지 실행으로 확인한다. PostToolUse `guard` 훅이
   추가 위반을 돌려주면 그 자리에서 반영한다.

5. **보고** — 고친 항목과 남은(확인 필요) 항목을 분리해 정직하게 보고한다. 다 못 고쳤으면 그렇게 말한다.
