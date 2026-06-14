#!/usr/bin/env bash
# ===================================================================
# 평가 회귀 게이트 (로드맵 #5) — 실효성/구조 지표가 회귀하면 비0 종료.
#
# 전제: KS(8095)·MO(8096)가 실행 중이고 코퍼스가 적재돼 있어야 한다(브링업: docs/evaluation-hybrid.md).
#   예) docker compose up(pgvector ×2) → ollama serve + bge-m3[/exaone] → MO(postgres,local) →
#       KS(postgres,localmodel[,llmrerank], METADATA_ENABLED=true) → POST /etl/run.
# 사용: bash scripts/eval-gate.sh         (환경변수 KS_URL/MO_URL/GOLD 로 재정의 가능)
#
# 검사하는 게이트(누적 개선의 회귀 방지):
#   - MO  : full microRecall ≥ 0.95, fuzzy ≥ full (pg_trgm 무회귀)
#   - KS  : integrated 정형 P@3 == 1.0 (MO 코드 필터), metadataAddsOverVector == true (KS→MO 구조 실효성)
#   - KS  : reranked 전체 P@3 ≥ integrated (리랭킹 무회귀; llmrerank 비활성 시 동일)
# ===================================================================
set -u
KS_URL=${KS_URL:-http://localhost:8095}
MO_URL=${MO_URL:-http://localhost:8096}
GOLD=${GOLD:-large}

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

echo "[eval-gate] KS integrated ablation (gold=$GOLD) ..."
curl -s --max-time 600 "$KS_URL/api/admin/evaluation/integrated?gold=$GOLD&k=3&limit=10" -o "$tmp/ks.json"
echo "[eval-gate] MO recall ..."
curl -s --max-time 180 "$MO_URL/api/admin/evaluation/recall" -o "$tmp/mo.json"

PYTHONIOENCODING=utf-8 python - "$tmp/ks.json" "$tmp/mo.json" <<'PY'
import sys, json

ks = json.load(open(sys.argv[1], encoding="utf-8")).get("data")
mo = json.load(open(sys.argv[2], encoding="utf-8"))
mo = mo.get("data", mo)
if ks is None or mo is None:
    print("[eval-gate] FAIL: 응답 파싱 불가 (앱이 실행 중인지 확인)")
    sys.exit(2)

ok = True
def ge(name, actual, thr):
    global ok
    p = actual >= thr - 1e-9
    ok = ok and p
    print(f"  [{'PASS' if p else 'FAIL'}] {name}: {actual:.4f} >= {thr}")
def truthy(name, actual):
    global ok
    ok = ok and bool(actual)
    print(f"  [{'PASS' if actual else 'FAIL'}] {name}: {actual}")

print("== MO (재현율/pg_trgm) ==")
ge("MO full microRecall", mo["full"]["microRecall"], 0.95)
ge("MO fuzzy >= full microRecall", mo["fuzzy"]["microRecall"], mo["full"]["microRecall"])

print("== KS (KS→MO 구조 + 리랭킹) ==")
ge("KS integrated 정형 P@3", ks["integrated"]["structured"]["precisionAtK"], 1.0)
truthy("KS metadataAddsOverVector", ks["interpretation"]["metadataAddsOverVector"])
ge("KS reranked 전체 P@3 >= integrated",
   ks["reranked"]["overall"]["precisionAtK"], ks["integrated"]["overall"]["precisionAtK"])

print("[eval-gate] GATE:", "PASS" if ok else "FAIL")
sys.exit(0 if ok else 1)
PY
