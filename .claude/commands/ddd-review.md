---
description: 변경된 Java/Kotlin 파일을 ddd-reviewer 서브에이전트로 DDD 감사하고 위반 목록만 회수
argument-hint: "[비교 기준 ref · 기본 origin/main]"
allowed-tools: Bash(git diff:*), Bash(git merge-base:*), Task
---

기준 ref(`$1`, 비어 있으면 `origin/main`) 대비 변경된 도메인 코드를 DDD 관점에서 감사한다.
이 커맨드는 **오케스트레이터**다 — 직접 리뷰하지 말고 `ddd-reviewer` 서브에이전트에 위임해
컨텍스트 방화벽을 유지하고 위반 목록·결론만 회수한다.

## 절차

1. 변경 파일 산출 (기준 ref 미지정 시 `origin/main`):
   !`base="${1:-origin/main}"; git diff --name-only "$(git merge-base HEAD "$base" 2>/dev/null || echo "$base")"...HEAD -- '*.java' '*.kt' 2>/dev/null || git diff --name-only "$base" -- '*.java' '*.kt'`

2. 위 목록이 비어 있으면 "변경된 도메인 코드 없음"이라고만 보고하고 종료한다.

3. 목록이 있으면 **`ddd-reviewer` 서브에이전트를 Task 로 실행**한다. 프롬프트에:
   - 감사 대상 파일 경로 목록(2단계 결과)을 그대로 전달.
   - "각 파일을 읽고 도메인 순수성·DIP·애그리거트 경계·빈약 모델·VO/불변성을 점검하라.
     `file:line — [규칙] 위반 요약 — 권장 수정` 형식의 목록만, 위반이 없으면 'DDD 위반 없음'만 반환하라"고 지시.

4. 서브에이전트가 돌려준 결론을 **그대로** 사용자에게 전달한다. 임의로 코드를 고치지 않는다
   (수정은 `/ddd-fix` 의 역할). 추측·확대 해석 금지 — 서브에이전트가 '확인 필요'로 표시한 항목은
   그대로 표시한다.
