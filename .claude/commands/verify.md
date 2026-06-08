---
description: 하네스 훅 셀프테스트 + Gradle 테스트 + ArchUnit 게이트를 실행해 실행 기반 검증
allowed-tools: Bash(./scripts/verify-harness.sh), Bash(./gradlew:*), Bash(cd archunit*)
---

카파시 4원칙의 **실행 기반 검증**을 한 번에 수행한다. "맞아 보인다"가 아니라 실제 실행으로 증명한다.
각 단계의 출력을 **처음부터 끝까지 정독**하고, 실패는 추측으로 덮지 말고 있는 그대로 보고한다.

## 절차

1. **하네스 훅 셀프테스트** — 훅이 차단/통과/경고를 결정론적으로 내는지 확인:
   !`./scripts/verify-harness.sh`

2. **단위 테스트** — 루트에 `gradlew` 가 있을 때만 실행(없으면 건너뛰고 그 사실을 보고):
   !`[ -x ./gradlew ] && ./gradlew test || echo "gradlew 없음 — 템플릿 단독 모드, 단위 테스트 건너뜀"`

3. **ArchUnit 게이트** — 컴파일된 클래스 그래프로 구조 규칙(레이어/DIP/애그리거트/VO) 검증:
   !`cd archunit && ./gradlew test`

## 보고 규칙

- 세 단계의 통과/실패를 표로 요약하고, 실패가 있으면 해당 stderr/스택트레이스의 핵심 줄을 인용한다.
- 하나라도 실패하면 전체를 ❌ 로 표시한다. 일부만 통과한 것을 ✅ 로 포장하지 않는다.
- 다음 행동(예: `/ddd-fix` 로 위반 수정)을 제안하되, 검증 실패를 숨긴 채 작업을 끝내지 않는다.
