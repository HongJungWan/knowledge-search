# 하이브리드 검색 실효성 평가 (KEYWORD vs HYBRID)

## 목적
운영 질의가 정형 50% / 비정형 50% 로 관찰되어, 본문 벡터(pgvector)를 더한 하이브리드 검색이
**비정형 절반의 재현율을 실제로 키우는지** + **정형 절반을 회귀시키지 않는지** 를 측정한다.
가정만으로 채택하지 않고 수치로 판정한다(채택 게이트).

## 구성
- 정답셋: `src/main/resources/evaluation/gold_search.csv` (정형 6 + 비정형 6 = 50/50).
  - 관련성은 반환 레코드 `title` 의 부분문자열 포함으로 판정 — 자동 증분 id 에 비의존.
- arm: **KEYWORD**(임베딩 없이 키워드 전용) vs **HYBRID**(질의 임베딩 + RRF 융합). 동일 포트로 구동.
- 지표: `recall@k`, `MRR`, `nDCG@k` (전체/정형/비정형 계층별). 지표 계산은 `RetrievalMetrics`(단위테스트로 고정).
- 채택 게이트: `HYBRID.비정형.recall@k > KEYWORD.비정형.recall@k` **AND** `HYBRID.정형.recall@k ≥ KEYWORD.정형.recall@k`.

## 실행 (도커 pgvector + Ollama 필요)
```bash
docker compose -f docker/docker-compose.postgres.yml up -d      # pgvector/pg16
ollama pull bge-m3 && ollama serve                              # 로컬 임베딩(1024d, 한국어/다국어)

# (A) 통합 테스트로 게이트 검증
RUN_SEMANTIC_EVAL=true ./gradlew test --tests '*HybridSearchEvaluationIntegrationTest'

# (B) 앱 기동 후 REST 로 리포트 조회
./gradlew bootRun --args='--spring.profiles.active=postgres,localmodel'
curl -X POST localhost:8095/etl/run                             # 적재(save 시 임베딩 자동 기록)
curl 'localhost:8095/api/admin/evaluation/hybrid?k=3&limit=10'  # KEYWORD vs HYBRID + 게이트
```
> 기본 해싱 스텁(localmodel/bedrock 미지정)으로는 배선·마이그레이션·RRF SQL 만 검증된다(수치 무의미).
> 실제 재현율은 bge-m3(localmodel) 또는 Titan v2(bedrock)에서 나온다. CI 는 스텁 수치로 게이팅하지 않는다.

## 결과 (2026-06-14 E2E 실측)
환경: KS `postgres,localmodel`(8095) + MO `postgres,local`(8096, Postgres에 사전 시드 184용어/292동의어/96코드값 + pg_trgm) + Ollama **bge-m3 1024d** + `metadata.enabled=true`(KS가 MO 실제 호출). 코퍼스 6문서, 골드 12질의(정형6/비정형6).

### A. 하이브리드 (리포지토리 레벨, `/api/admin/evaluation/hybrid`, k=3) — 게이트 PASS
| arm | overall | 정형 | 비정형 |
|---|---|---|---|
| KEYWORD | 0.5833 | 0.8333 | 0.3333 |
| HYBRID | **1.0** | 1.0 | **1.0** |
→ 비정형 0.3333→1.0(향상), 정형 무회귀. **벡터 arm은 명백히 실효성 있음.**

### B. KS→MO 참조 구조 ablation (`/api/admin/evaluation/integrated`, k=3) — 핵심
| arm | overall | 정형 | 비정형 |
|---|---|---|---|
| KEYWORD (MO✗·벡터✗) | 0.5833 | 0.8333 | 0.3333 |
| META (MO✓·벡터✗) | 0.5833 | 0.8333 | 0.3333 |
| VECTOR (MO✗·벡터✓) | 1.0 | 1.0 | 1.0 |
| INTEGRATED (MO✓·벡터✓) | 1.0 | 1.0 | 1.0 |
- `metadataCodeValueCoverage = 0.083` (12질의 중 **1건**만 MO가 코드값 산출 — "미정산"→settlement_status=PENDING)
- `metadataHelpsStructured=false`, `vectorHelpsUnstructured=true`, **`metadataAddsOverVector=false`**

### C. MO 단독 (`/api/admin/evaluation/recall`, pg_trgm 라이브): BASELINE 0.3438 / FULL 1.0 / FUZZY 1.0

### 판정 — "KS→MO 참조 구조가 50/50에서 실효성이 있는가?"
**현재 데이터 기준: 검색 재현율에 추가 값이 사실상 없음.** 벡터가 비정형을 완전히 커버(1.0)하고, MO 참조는 벡터 위에 기여 0(INTEGRATED=VECTOR), 단독으로도 KEYWORD 대비 무기여. 단 **두 교란요인**이 이 결론을 좌우한다:
1. **토이 코퍼스(6문서)**: recall@3가 6문서에선 벡터에 자명히 1.0 → MO의 **정밀 코드필터 가치(대규모·정밀 압력에서 발현)** 를 측정 불가하게 가림.
2. **어휘 불일치(coverage 8.3%)**: MO 매핑 어휘(settlement.settlement_status, fee_policy.fee_rate…)와 KS 문서 `code_values` 태그(settlement_cycle/merchant_grade/payout_rule/adjustment_type)가 **settlement_status에서만 정렬**. MO가 기여할 코드값 자체가 거의 없음.

### 개선 방향
- **코퍼스 확대 후 재측정**: 키워드가 겹치는 대량 문서에서 코드값 필터가 정밀도(precision)를 끌어올리는지 확인해야 MO 참조의 진짜 값을 잼.
- **MO↔KS 코드 어휘 정렬**: MO가 KS 문서 태그 어휘(merchant_grade 등)로도 해석하도록 매핑 보강 → coverage↑.
- **정렬 전까지 구조 단순화 검토**: KS→MO 참조를 "코드값이 실제로 잡히는 정형 질의"에만 선택 적용(매 질의 2-홉 비용 회피). 비정형은 벡터 단독으로 충분.
- MO의 질의 이해(동의어→표준어, FULL 1.0 vs BASELINE 0.34)는 **SQL 필터 생성 등 다른 용도**에서 값을 하므로 폐기가 아니라 **연결 방식 재설계**가 결론.
