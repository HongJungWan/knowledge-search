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

## 결과 (2026-06-14 재측정 — 대량 코퍼스 · 정밀도 압력)
교란요인을 제거한 재측정. 코퍼스 **80문서**(settlement_status 4값 × 20, `scripts/gen-corpus.py`): 모든 문서가 동일한 "정산 처리 절차" 공통 단락을 공유하고 상태는 **쿼리 표면형과 다른 패러프레이즈로만** 약하게 노출 → 텍스트로는 상태 구분 불가, **코드값(settlement_status)만이 정확히 구분**. relevance는 **코드값 기준**(`expectedCode`, relevantCount=20), 지표는 **precision@3**(코드 필터의 정밀도 기여). 골드 12(정형6=MO 해석 가능 표면형 미정산/정산완료/홀드/캔슬, 비정형6=패러프레이즈). 환경 동일(KS postgres,localmodel + MO postgres,local + bge-m3 + metadata.enabled).

### `/api/admin/evaluation/integrated?gold=large&k=3` — coverage 0.4167
| arm | P@3 전체 | P@3 정형 | P@3 비정형 | R@3 전체 |
|---|---|---|---|---|
| KEYWORD (MO✗ 벡터✗) | 0.333 | 0.333 | 0.333 | 0.050 |
| META (MO✓ 벡터✗) | 0.667 | **1.000** | 0.333 | 0.100 |
| VECTOR (MO✗ 벡터✓) | 0.500 | 0.333 | 0.667 | 0.075 |
| INTEGRATED (MO✓ 벡터✓) | **0.833** | **1.000** | 0.667 | 0.125 |
- `metadataHelpsStructured=true`, `vectorHelpsUnstructured=true`, **`metadataAddsOverVector=true`**
- (R@3 상한은 3/20=0.15 — 질의당 관련 20건이므로 정밀도가 본질 지표. INTEGRATED 정형 R@3=0.15 = 상위3 전부 정답.)

### 판정 — 결론이 뒤집힘
**정밀도 압력 하에서 KS→MO 참조 구조는 실효성이 있다.** 텍스트가 상태를 구분 못 하는 상황에서:
- **정형 질의: MO 코드 필터만이 정밀도를 0.333→1.000으로** 끌어올린다(키워드·벡터는 혼동 상태들 사이에서 ~0.33).
- **비정형 질의: 벡터가 담당**(패러프레이즈, MO 미해석) — precision 0.333→0.667.
- **INTEGRATED가 전 구간 최상**(전체 P@3 0.833) — MO가 벡터 위에 +0.333 정밀도를 더한다.
- 직전 6문서 토이 코퍼스의 "MO 무기여"는 **코퍼스 규모의 artifact**였음이 확인됨(벡터가 6문서에선 자명히 1.0).

### 남은 한계 (개선 대상 — `docs/improvement-roadmap.md`)
- **coverage 0.42**: 정형 질의의 표면형만 MO가 코드로 해석. 비정형·미정렬 어휘는 여전히 미해석 → MO↔KS 코드 어휘 정렬이 다음 레버.
- 비정형 P@3 0.667(미완) → 리랭킹/청킹으로 보강 여지.

## 결과 (2026-06-14 — 로드맵 #1 어휘 정렬 적용 후)
MO `/resolve` 가 Term 동의어로 확장된 토큰뿐 아니라 **잔여 질의의 모든 코드값 표면형**("홀드"→HOLD, "캔슬"→CANCELED, "대기"→PENDING 등)을 코드값 컬럼 매핑으로 내려주도록 확장(`ResolveService.buildColumnMappings`/`resolveCodeCandidates`). BASELINE 순수성·재현율 임계값 무회귀 확인.

| 지표 | #1 전 | **#1 후** |
|---|---|---|
| MO codeValue coverage | 0.417 | **0.583** |
| INTEGRATED P@3 전체 | 0.833 | **0.917** |
| INTEGRATED P@3 정형 | 1.000 | 1.000 |
| INTEGRATED P@3 비정형 | 0.667 | **0.833** |
| META P@3 비정형 | 0.333 | **0.500** |

- 어휘 정렬로 **coverage↑(0.42→0.58)**, 정형은 이미 1.000 유지, **비정형 정밀도까지 0.667→0.833** 향상 — "대기" 같은 코드 표면형이 패러프레이즈에 섞이면 코드 필터가 추가 작동.
- `metadataAddsOverVector=true` 유지(VECTOR 0.500 → INTEGRATED 0.917, 마진 +0.417로 확대).
- 남은 헤드룸: 비정형 0.833(코드 표면형 없는 순수 패러프레이즈는 여전히 벡터 의존) → 리랭킹/청킹(로드맵 #3·#4).

## 결과 (2026-06-14 — 로드맵 #3 청킹 A/B, 효과 없음/null)
장문 코퍼스(`sample/settlement-source-long.json`, 80문서 × ~11문장, 상태 구분 문장 1개가 보일러플레이트에 희석)에서 통째-임베딩 vs 청크 임베딩(문서의 최상 청크로 랭킹)을 비교(`search.chunking.enabled` 토글, 동일 데이터).

| arm | OFF(통째) P@3 ov/정형/비정형 | ON(청크) P@3 ov/정형/비정형 |
|---|---|---|
| VECTOR | 0.417 / 0.333 / 0.500 | 0.417 / 0.333 / 0.500 |
| INTEGRATED | 0.833 / 1.000 / 0.667 | 0.833 / 1.000 / 0.667 |

- **청킹이 개선하지 못함(동일)**. 원인은 *희석*이 아니라, 상태 구분 문장이 공통 보일러플레이트와 어휘("정산/지급/거래")를 공유해 **의미 신호 자체가 약하기 때문** — granularity(청킹)로는 못 가른다. (구현 중 발견·교정: 제네릭 제목을 모든 청크에 prepend 하면 오히려 악화(VECTOR 0.361) → 제목은 별도 청크, 문장은 순수 문장으로.)
- **결론**: 청킹은 *관련 정보가 국소적이고 변별적인* 진짜 장문 이질 문서에서 값을 한다. 본 합성 코퍼스는 그 경우가 아니므로 **기본 OFF 유지**(플래그·V3·TextChunker·청크-aware 벡터 arm 은 구현·검증 완료, 필요 시 활성). 측정 우선.
- **잔여 비정형(0.667)의 진짜 레버는 #4 리랭킹** — 의미적으로 유사한 상태를 *변별*하는 것(코드 표면형 없는 L08/L09/L10/L12 류). 청킹과 직교.

## 결과 (2026-06-14 — 로드맵 #4 리랭킹, 효과 있음)
INTEGRATED 후보(top-10)를 Ollama LLM(EXAONE 3.5 2.4B, 한국어)으로 **리스트와이즈 재정렬**해 top-3 산출(`Reranker` 포트 + `LlmJudgeReranker`, `llmrerank` 프로파일). 동일 장문 코퍼스.

| arm | P@3 전체 | P@3 정형 | P@3 비정형 | MRR | nDCG@3 |
|---|---|---|---|---|---|
| INTEGRATED | 0.833 | 1.000 | 0.667 | 0.854 | 0.833 |
| **RERANKED** | **0.917** | 1.000 | **0.833** | **0.917** | **0.917** |

- **리랭킹이 잔여 비정형을 0.667→0.833, 전체 0.833→0.917**(MRR·nDCG 동반 상승). `rerankImprovesUnstructured=true`. 진단(의미 변별 레버) 입증 — 청킹(granularity)이 못 올린 부분을 리랭킹이 올림.
- **구현 교훈**: 리랭커 snippet 에 *구별 정보*가 들어와야 한다. 처음엔 본문 앞 120자만 넘겨 효과 0(앞부분이 공통 보일러플레이트) → 800자로 늘려 상태 구분 문장을 포함시키자 효과 발생. (장문 운영 문서에선 #3 청킹으로 관련 청크만 골라 리랭커에 주는 조합이 자연스러움 — 두 기법의 시너지.)
- 비용: 질의당 LLM 호출 1회(리스트와이즈). NoOp 기본, llmrerank 프로파일에서만 활성.

## 결과 (2026-06-14 — 로드맵 #6 로컬 OpenSearch 하이브리드, 운영 타깃 검증)
운영 검색 타깃(OpenSearch)을 로컬 Docker(AWS 불필요)로 구현·측정. `OpenSearchKnowledgeRecordRepositoryImpl`(@Profile `opensearch`): BM25 + k-NN 두 질의 + **Java RRF(k=60) 융합**(pgvector SQL RRF 와 동일 개념 이관), 정형 필터(domain·code_values term)는 두 arm 모두에 적용. 프로파일 `postgres,localmodel,opensearch`(Postgres=SearchLog/배치, OpenSearch=KnowledgeRecord 검색). 코퍼스 80문서(large) 색인, gold=large.

| arm | P@3 전체 | P@3 정형 | P@3 비정형 | (참고: pgvector 경로 전체) |
|---|---|---|---|---|
| KEYWORD | 0.583 | 0.167 | 1.000 | 0.583 |
| META | 0.750 | 0.500 | 1.000 | — |
| VECTOR | 0.694 | 0.389 | 1.000 | — |
| **INTEGRATED** | **1.000** | **1.000** | **1.000** | 0.917 |

- **OpenSearch INTEGRATED P@3=1.000** — pgvector 경로(0.917)보다 우수. `metadataAddsOverVector=true`(코드 필터로 정형 1.000), 비정형은 **BM25만으로 1.000**.
- **핵심 차이**: OpenSearch **BM25**가 한국어 패러프레이즈를 Postgres LIKE/ILIKE 보다 훨씬 잘 매칭(KEYWORD 비정형 0.333→1.000). 즉 운영 검색엔진(BM25 분석기 + k-NN)이 dev pgvector 보다 강함 — 이관 가치 입증.
- **이관 매핑 확인**: pgvector 의 정형 필터+RRF 하이브리드가 OpenSearch(term filter + BM25/kNN + RRF)로 1:1 옮겨짐. 운영에선 임베딩을 Bedrock Titan 으로, RRF 를 OpenSearch 네이티브 hybrid 파이프라인으로 교체하면 됨(코드 구조 동일).
- 남은 #6: Bedrock Titan 임베딩(`BedrockEmbeddingProvider` 구현 완료, AWS 자격증명 시 활성) — 인프라 확보 후.

## 결과 (2026-06-14 — 로드맵 #7 한국어 형태소 분석기 nori, 토큰/매칭 레벨 측정)
OpenSearch BM25 의 한국어 분석을 `standard` → `nori`(형태소)로 교체(커스텀 이미지 `opensearch-nori.Dockerfile` + 어댑터 `opensearch.text-analyzer` 설정화). **합성 코퍼스는 이미 포화(OpenSearch INTEGRATED P@3=1.000)라 retrieval 지표로는 추가 측정 불가** → 개선을 토큰화/매칭 레벨에서 직접 측정한다.

| 측정 | standard | nori |
|---|---|---|
| `_analyze("미정산을 정산했습니다")` | `[미정산을, 정산했습니다]` | `[미, 정산, 정산]` |
| 본문 "정산을 마감했다" · 질의 "정산" 매칭 | **0건** | **1건** |

- **결론**: standard 는 조사("을")·활용("했습니다")을 분리 못 해 surface form 토큰을 만든다 → 조사만 다른 질의를 BM25 가 놓친다. nori 는 형태소로 분해해 매칭한다. **실데이터/조사 포함 질의에서 BM25 품질이 오르는 production 개선**(이 합성 코퍼스에선 띄어쓰기가 정돈돼 표준도 동작해 P@3 1.000 유지 — 무회귀).
- 어댑터는 `text-analyzer` 설정으로 standard/nori 선택(기본 standard). nori 이미지에서 `OPENSEARCH_TEXT_ANALYZER=nori`.

## 측정 한계 & 다음
합성 코퍼스가 **포화**(OpenSearch INTEGRATED P@3=1.000)에 도달해, 이후 기법(#7 RRF k 튜닝 등)은 이 코퍼스의 retrieval 지표로 차이를 낼 수 없다. **다음 큰 레버는 실데이터 평가셋**(운영 문서 + `search_log` 질의 로그) — 거기서 #3 청킹·리랭킹·nori·RRF 의 실운영 가치를 비로소 정량 비교할 수 있다.
