#!/usr/bin/env bash
# 하네스 훅 자가검증 (자기완결형 — 템플릿 fixtures 불필요).
# 실제 소스 전수 + 합성 위반/정상 파일로 가드 훅이 결정론적으로 동작함을 증명한다.
set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
H="$ROOT/.claude/hooks/harness.mjs"
pass=0; fail=0
j(){ printf '{"tool_name":"Write","tool_input":{"file_path":"%s"}}' "$1"; }
# ROOT 상대 경로로 변환해 전달 — Windows bash(MSYS)의 /c/... 절대 경로는 node fs 가
# 해석하지 못해 가드가 조용히 통과(exit 0)해 자가검증이 무의미해진다. node 는 ROOT 에서 실행.
guard_exit(){ j "${1#"$ROOT"/}" | (cd "$ROOT" && node "$H" guard) >/dev/null 2>&1; echo $?; }

# 합성 파일은 실제 소스루트 하위 임시 패키지에 두고 종료 시 정리(가드는 경로로 레이어를 판정).
SELF="$ROOT/src/main/java/__harness_selftest__"
trap 'rm -rf "$SELF"' EXIT
mkdir -p "$SELF/domain"

# 1) 실제 소스 전수 → 차단 0 기대
blocks=0; scanned=0
while IFS= read -r f; do
  case "$f" in *"__harness_selftest__"*) continue;; esac
  scanned=$((scanned+1))
  [ "$(guard_exit "$f")" -eq 2 ] && blocks=$((blocks+1))
done < <(find "$ROOT/src/main/java" -name '*.java')
# 빈 입력(find 0건)이면 가짜 성공이 되므로 검사 파일 수 > 0 을 함께 단언한다.
if [ "$blocks" -eq 0 ] && [ "$scanned" -gt 0 ]; then echo "  ✔ 실제 소스 가드 0 차단 (${scanned}개 검사)"; pass=$((pass+1)); else echo "  ✗ 실제 소스 차단 ${blocks}건 / 검사 ${scanned}개"; fail=$((fail+1)); fi

# 2) 합성 도메인 위반(공개 setter) → 차단(exit 2) 기대
cat > "$SELF/domain/SelfTestBad.java" <<'EOF'
package __harness_selftest__.domain;
public class SelfTestBad {
    private String name;
    public void setName(String name) { this.name = name; }
}
EOF
if [ "$(guard_exit "$SELF/domain/SelfTestBad.java")" -eq 2 ]; then echo "  ✔ 도메인 공개 setter 위반 차단"; pass=$((pass+1)); else echo "  ✗ 합성 위반 미차단"; fail=$((fail+1)); fi

# 3) 합성 정상 파일 → 통과(exit 0) 기대
cat > "$SELF/domain/SelfTestOk.java" <<'EOF'
package __harness_selftest__.domain;
public final class SelfTestOk {
    private final String name;
    public SelfTestOk(String name) { this.name = name; }
    public String name() { return name; }
}
EOF
if [ "$(guard_exit "$SELF/domain/SelfTestOk.java")" -eq 0 ]; then echo "  ✔ 정상 도메인 파일 통과"; pass=$((pass+1)); else echo "  ✗ 정상 파일 오차단"; fail=$((fail+1)); fi

echo "── pass=$pass fail=$fail"
if [ "$fail" -eq 0 ]; then echo "✅ 하네스 훅 검증 완료"; exit 0; else echo "❌ 실패 있음"; exit 1; fi
