#!/usr/bin/env bash
# 하네스 훅 자가 검증. 훅 입력(JSON)을 모사해 차단/통과 종료코드를 단언한다.
# "에이전트가 작성한 코드에 훅이 잘 도는지"를 결정론적으로 증명한다.
set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
H="$ROOT/.claude/hooks/harness.mjs"
pass=0; fail=0

# $1 기대 종료코드, $2 설명, $3 하위명령, $4 stdin JSON
expect() {
  local want="$1" desc="$2" sub="$3" json="$4"
  printf '%s' "$json" | node "$H" "$sub" >/dev/null 2>&1
  local got=$?
  if [ "$got" -eq "$want" ]; then echo "  ✔ $desc (exit $got)"; pass=$((pass+1));
  else echo "  ✗ $desc — 기대 $want, 실제 $got"; fail=$((fail+1)); fi
}

# exit 코드 + stderr keyword 단언. $1 want exit, $2 keyword, $3 설명, $4 파일.
# (severity 가 block/warn 어느 쪽이든 검증할 수 있도록 exit 코드를 인자로 받는다.)
expect_msg() {
  local want="$1" kw="$2" desc="$3" file="$4"
  local err; err="$(mk "$file" | node "$H" guard 2>&1 >/dev/null)"; local got=$?
  if [ "$got" -eq "$want" ] && printf '%s' "$err" | grep -q "$kw"; then
    echo "  ✔ $desc (exit $got + 메시지)"; pass=$((pass+1));
  else echo "  ✗ $desc — exit $got / 메시지 누락($kw)"; fail=$((fail+1)); fi
}

DOM="$ROOT/fixtures/bad/src/main/java/com/example/domain"
APP="$ROOT/fixtures/bad/src/main/java/com/example/application"
BAD="$DOM/order/OrderService.java"
CART="$DOM/order/Cart.java"
IMPL="$DOM/order/OrderRepositoryImpl.java"
MONEY="$DOM/order/Money.java"
REPO="$DOM/order/OrderRepository.java"
CHECKOUT="$DOM/checkout/Checkout.java"
SUBS="$DOM/subscription/Subscription.java"
ANNOT="$DOM/order/AnnotatedOrder.java"
JDBC="$APP/JdbcOrderService.java"
EVENTPUB="$DOM/event/OrderShipped.java"
TIMEBOMB="$DOM/order/TimeBomb.java"
RANDOMID="$DOM/order/RandomId.java"
CLEAN="$ROOT/fixtures/clean/src/main/java/com/example/domain/order/Order.java"
mk() { echo "{\"tool_name\":\"Write\",\"tool_input\":{\"file_path\":\"$1\"}}"; }

echo "▶ guard — 차단(block)"
expect 2 "도메인 infra import + 필드주입 → 차단" guard "$(mk "$BAD")"
expect 2 "도메인 @Setter/public setter → 차단"   guard "$(mk "$CART")"
expect 2 "도메인 *RepositoryImpl(DIP) → 차단"    guard "$(mk "$IMPL")"
expect 2 "@ValueObject 가변(불변성) → 차단"       guard "$(mk "$MONEY")"
expect 2 "도메인 *Repository 클래스(DIP) → 차단"  guard "$(mk "$REPO")"
expect 0 "리치 도메인 엔티티 → 통과(경고 없음)"   guard "$(mk "$CLEAN")"

echo "▶ guard — 신규 차단 룰(A·B·C·I·J)"
expect 2 "도메인 Spring 스테레오타입/@Transactional → 차단" guard "$(mk "$ANNOT")"
expect 2 "응용→인프라 import(JdbcTemplate) → 차단"          guard "$(mk "$JDBC")"
expect 2 "도메인 EventPublisher import → 차단"              guard "$(mk "$EVENTPUB")"
expect 2 "도메인 LocalDateTime.now() 직접 호출 → 차단"       guard "$(mk "$TIMEBOMB")"
expect 2 "도메인 UUID.randomUUID() 직접 호출 → 차단"         guard "$(mk "$RANDOMID")"

echo "▶ guard — 메시지 + exit 코드 (현 정책: checks.aggregateBoundary/idReference=block)"
expect_msg 2 "애그리거트 경계" "다른 애그리거트 @AggregateInternal 참조 → 차단" "$CHECKOUT"
expect_msg 2 "ID 참조"        "다른 AR 객체 직접 참조 → 차단"                  "$SUBS"

echo "▶ protect (PreToolUse)"
expect 2 "Flyway 마이그레이션 수정 → 차단" protect \
  '{"tool_input":{"file_path":"/x/src/main/resources/db/migration/V1__init.sql"}}'
expect 0 "일반 파일 → 통과"            protect '{"tool_input":{"file_path":"/x/src/main/java/com/example/domain/Order.java"}}'

echo "▶ bash (PreToolUse Bash)"
expect 2 "mvn 사용 → 차단"       bash '{"tool_input":{"command":"mvn install"}}'
expect 2 "전역 gradle 사용 → 차단" bash '{"tool_input":{"command":"gradle build"}}'
expect 0 "./gradlew 사용 → 통과"   bash '{"tool_input":{"command":"./gradlew build"}}'

echo "▶ checklist (Stop)"
expect 2 "완료 직전 → 자가점검 강제" checklist '{}'
expect 0 "이미 점검 중 → 통과(루프 방지)" checklist '{"stop_hook_active":true}'

echo "▶ config 유효성"
if node -e "JSON.parse(require('fs').readFileSync('$ROOT/.claude/hooks/harness.config.json','utf8'))" 2>/dev/null; then
  echo "  ✔ harness.config.json 파싱 OK"; pass=$((pass+1)); else echo "  ✗ config 파싱 실패"; fail=$((fail+1)); fi

echo "▶ 슬래시 커맨드 (.claude/commands/)"
for cmd in ddd-review verify ddd-fix; do
  f="$ROOT/.claude/commands/$cmd.md"
  if [ -f "$f" ] && head -n 1 "$f" | grep -q '^---' && grep -q '^description:' "$f"; then
    echo "  ✔ /$cmd 존재 + frontmatter(description) OK"; pass=$((pass+1));
  else echo "  ✗ /$cmd 누락 또는 frontmatter 불량"; fail=$((fail+1)); fi
done

echo "▶ MCP 템플릿 (.mcp.json)"
if node -e "const m=JSON.parse(require('fs').readFileSync('$ROOT/.mcp.json','utf8')); if(!m.mcpServers||!Object.keys(m.mcpServers).length)process.exit(1)" 2>/dev/null; then
  echo "  ✔ .mcp.json 파싱 OK + mcpServers 존재"; pass=$((pass+1)); else echo "  ✗ .mcp.json 파싱 실패 또는 mcpServers 없음"; fail=$((fail+1)); fi

echo
echo "결과: $pass 통과 / $fail 실패"
[ "$fail" -eq 0 ] && echo "✅ 하네스 훅 검증 완료" || { echo "❌ 실패 있음"; exit 1; }
