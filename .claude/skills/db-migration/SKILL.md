---
name: db-migration
description: DB 스키마 변경/마이그레이션 작업 시 로드. Flyway 규칙 — 기존 마이그레이션 수정 금지(훅 차단), 새 버전 추가.
---

# DB 마이그레이션 가이드 (Flyway)

- 위치: `src/main/resources/db/migration/`
- 파일명: `V{번호}__{설명}.sql` (예: `V30__add_order_status.sql`). 번호는 연속, 건너뛰지 않는다.
- **기존 마이그레이션 파일은 절대 수정/삭제하지 않는다.** 이미 적용된 스크립트는 불변.
  스키마 변경은 항상 **새 V 버전**을 추가하는 방식으로. (PreToolUse 훅이 기존 파일 수정을 차단)
- 롤백이 필요하면 보상 마이그레이션(새 버전)으로 처리한다.
- 적용 확인: `./gradlew bootRun` (앱 기동 시 Flyway 가 펜딩 마이그레이션 적용) 또는 `./gradlew flywayInfo`.
