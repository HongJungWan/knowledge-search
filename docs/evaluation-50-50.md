# E2E 50/50 실효성 평가 — knowledge-search × metadata-ontology

정형 데이터 50% / 비정형 데이터 50% 환경에서 두 서비스의 End-to-End 연동이 **실효성**이 있는지
판정하는 절차·지표·합격기준. 기존 평가 하네스(`IntegratedEvaluationService`)를 재사용한다.

## 1. 가설 & 지표

벡터 베이스라인 위에 MO(2번째 서비스)를 더했을 때 층위별 이득이 측정되고 다른 층위 무회귀이면 "실효성 있음".

| # | 주장 | 측정 | 비교 arm |
|---|---|---|---|
| H1 | 벡터가 비정형 half에 기여 | `unstructured.recall@k` ↑ | VECTOR > KEYWORD |
| H2 | MO 코드필터가 정형 half에 기여 | `structured.precision@k` ↑ | META > KEYWORD |
| H3 | **MO가 벡터 위에 가치 추가 (핵심)** | `overall.precision@k` 또는 `recall@k` ↑ | INTEGRATED > VECTOR |
| H4 | 하이브리드 전환의 정형 무회귀 | `structured.recall@k` 미하락 | INTEGRATED ≥ VECTOR |

- 정형 half: 주 `precision@k`, 보조 MO 코드값 커버리지. 비정형 half: 주 `recall@k`, 보조 `MRR`/`nDCG`.
- 핵심 증거 = `interpretation.metadataAddsOverVector` (metadata OFF→ON 델타 = INTEGRATED − VECTOR).
- arm 5종(KEYWORD/META/VECTOR/INTEGRATED/RERANKED)은 `IntegratedEvaluationService`에 이미 구현됨 — META/INTEGRATED arm은 골드의 codeValues 가 아니라 **실제 MO `/api/resolve`** 결과로 코드값을 얻는다(진짜 KS→MO 2-홉).

## 2. 데이터셋 (선결 작업 — 본 작업에서 구축·검증 완료)

| 산출물 | 경로 | 내용 |
|---|---|---|
| 코퍼스 | `src/main/resources/sample/settlement-source-eval.json` | 100 docs, **10 클래스 × 10**, 코드 차원 3종(settlement_status 4 / settlement_cycle 3 / contract_type 3). 공통 단락 공유로 정밀도 압력, 본문은 표면형 없는 패러프레이즈. 생성기: `scripts/gen-corpus.py` (`generate_eval`). |
| 골든셋 | `src/main/resources/evaluation/gold_search_eval.csv` | **40 쿼리, 정형 20 / 비정형 20**. 전부 코드 모드(`expectedCode`, `relevantCount=10`). 정형은 MO가 해석하는 표면형, 비정형은 표면형 없는 우회 표현(벡터 필요). |
| 무결성 게이트 | `GoldSearchEvalIntegrityTest` (항상 실행) | 40개·50/50·전행 코드모드·3차원 압박을 CI에서 강제(시맨틱 스택 불필요). |

### 어휘 정렬 — 측정으로 확인된 결과

> **MO 시드(`code_values.csv`/`mappings.csv`)는 수정 불필요.** 라이브 MO(`POST /api/resolve`)로 40개 쿼리를 전수 프로빙한 결과:
> - **정형 20/20** 모두 정확히 기대 코드로 해석됨 (미정산→PENDING, 월정산→MONTHLY, 위수탁계약→CONSIGNMENT 등).
> - 비정형 0/20 해석됨(전부 NONE) → 벡터가 진짜로 필요.
> - **전체 커버리지 = 20/40 = 0.50** → H2/H3 측정 전제(≥0.50) 충족.
>
> 즉 브리핑의 "~8% 어휘 불일치"는 MO 어휘 부재가 아니라 **이전 골드셋의 표현이 MO에 도달하지 못한 것**이었다. 50/50 골드셋을 MO가 실제로 해석하는 표면형으로 구성해 해소했다.

## 3. 테스트 환경 (로컬 Postgres+Ollama, AWS 제외)

RRF 하이브리드는 `PostgresKnowledgeRecordRepositoryImpl`에만 있으므로 **postgres+localmodel** 프로파일에서만 유의미.

```bash
# 1) pgvector
docker compose -f docker/docker-compose.postgres.yml up -d        # pg_isready 확인
# 2) Ollama bge-m3 (1024d, vector(1024) 컬럼과 일치)
ollama pull bge-m3 && ollama serve                                # curl localhost:11434/api/tags 확인
# 3) metadata-ontology (8096, local/H2 — 96 코드값 시드)
(cd ../metadata-ontology && ./gradlew bootRun)                    # /api/resolve {"query":"미정산"} → PENDING 확인
# 4) knowledge-search (8095, postgres+localmodel, metadata ON)
METADATA_ENABLED=true METADATA_BASE_URL=http://localhost:8096 \
  ./gradlew bootRun --args='--spring.profiles.active=postgres,localmodel'
# 5) eval 코퍼스 적재 (postgres save() 가 임베딩 동시 기록)
curl -X POST 'http://localhost:8095/etl/run'   # etl.source-resource=sample/settlement-source-eval.json 로 기동
```

> **AWS 제외 주석(모든 리포트에 부착):** postgres+localmodel(개발/스테이징 vehicle)에서 측정. Redshift 프로덕션 타깃은 RRF 미포팅으로 비정형 half가 keyword-only로 degrade — 본 수치는 AWS 배포의 상한선.

## 4. 실행 & 측정

```bash
# 5-arm ablation (50/50 다중 코드차원 골드셋)
curl 'http://localhost:8095/api/admin/evaluation/integrated?gold=eval&k=3&limit=10'
# 빠른 2-arm 게이트(H1+H4 예비)
curl 'http://localhost:8095/api/admin/evaluation/hybrid?k=3&limit=10'
# MO 자체 재현율(정형 exact-code 증거)
curl 'http://localhost:8096/api/admin/evaluation/recall'
# 회귀 게이트(기존 러너 재사용, eval 골드셋 지정) — 회귀 시 비0 종료
GOLD=eval bash scripts/eval-gate.sh
```

자동 회귀 가드(옵트인 테스트, 시맨틱 스택 필요):
```bash
RUN_SEMANTIC_EVAL=true ./gradlew test --tests '*IntegratedMetadataEvaluationIntegrationTest'
```
이 테스트가 H1·H2·H3·H4 + 커버리지(≥0.5)·비포화(<1.0) 전제를 단언한다. metadata OFF/ON 델타는 한 번의
`evaluate()` 안에서 VECTOR(=metaOff) vs INTEGRATED(=metaOn) arm으로 측정되므로 별도 재기동이 불필요하다.

## 5. 합격/불합격 판정

**효과적 결론(모두 성립):**
1. H1: `VECTOR.unstr.recall@3 − KEYWORD.unstr.recall@3 ≥ +0.10` (또는 `vectorHelpsUnstructured`)
2. H2: `metadataHelpsStructured == true`
3. H3(핵심): `metadataAddsOverVector == true`
4. H4: `INTEGRATED.struct.recall@3 ≥ VECTOR.struct.recall@3 − 1e-9`
5. 전제: 커버리지 ≥ 0.50 (미만 → "판정 불가, 어휘 수정")
6. 전제: `VECTOR.unstr.recall@3 < 1.0` (=1.0 → "판정 불가, 코퍼스 확대")

| 실패 모드 | 탐지기 | 신호 |
|---|---|---|
| 어휘 불일치 | `metadataCodeValueCoverage` | META≈KEYWORD & 커버리지<0.5 |
| 코퍼스 포화 | 전제 6 | VECTOR.unstr.recall@3=1.0 & INTEGRATED=VECTOR |
| 폴백 마스킹 | 커버리지==0 & 모든 MO arm이 KEYWORD/VECTOR로 붕괴 | `metadata.enabled=false` 또는 MO 다운 |
| 임베딩 미백필/스텁 | VECTOR≈KEYWORD(비정형) | localmodel 미활성 → Ollama·백필 확인 |
| Redshift 외삽 오류 | §3 주석 | postgres 수치를 AWS로 오인 |

## 6. 본 세션에서 실행한 것 / 남은 것

- ✅ **선결 데이터 구축·검증**: 코퍼스(100 docs·3차원), 50/50 골드셋(40), 무결성 테스트. 커버리지 0.50을 **라이브 MO로 전수 프로빙**해 확인(정형 20/20).
- ✅ **하네스/회귀 가드**: `gold=eval` 분기, `IntegratedMetadataEvaluationIntegrationTest`(H1~H4 단언), `GoldSearchEvalIntegrityTest`(항상 실행).
- ⚠️ **전체 시맨틱 측정(H1·H3 수치)**: pgvector + Ollama bge-m3 필요. 본 세션 환경은 Docker 데몬 미기동·Ollama 미설치로 실행 불가 — 모델 다운로드/설치는 사용자 승인 영역이라 미수행. §3~§4 절차로 사용자가 1회 실행하면 위 가드가 자동 판정한다.
