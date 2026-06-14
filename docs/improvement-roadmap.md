# 개선 로드맵 — knowledge-search × metadata-ontology

> 근거: `docs/evaluation-hybrid.md`(정밀도 압력 재측정), `metadata-ontology/docs/evaluation-recall.md`.
> 핵심 측정 사실: 정밀도 압력 하에서 **INTEGRATED P@3=0.833**(정형 1.000) > VECTOR 0.500 > KEYWORD 0.333.
> MO 코드 필터가 정형 정밀도를 0.333→1.000으로 끌어올림(구조 실효성 확인). 단 **MO 코드 해석 coverage=0.42**가 상한.

## 우선순위 요약
| # | 개선 | 문제(근거) | 기대효과 | 노력도 |
|---|---|---|---|---|
| 1 | **MO↔KS 코드 어휘 정렬 ✅완료(2026-06-14)** | coverage 0.42 — 절반만 코드 해석 | coverage 0.42→0.58, INTEGRATED P@3 0.833→0.917, 비정형 0.667→0.833 | 중 |
| 2 | 쿼리 라우팅(선택적 MO 호출) | 매 질의 2-홉(비정형엔 무기여) | 지연·비용↓, 구조 명확화 | 중 |
| 3 | **문서 청킹 ☑측정완료(null)** | 장문 본문 통째 임베딩 | 합성 코퍼스에선 효과 없음(동일) — 기본 OFF, 진짜 장문 이질문서용 | 중 |
| 4 | **리랭킹(LLM-judge) ✅완료(2026-06-14)** | 비정형 정밀도 미완(0.667) | 비정형 0.667→0.833, 전체 0.833→0.917 | 중 |
| 5 | **평가 상시화(회귀 게이트) ✅완료(2026-06-14)** | 측정이 수동 | scripts/eval-gate.sh 임계 검사·회귀 시 비0 종료 | 소 |
| 6 | **운영 경로(OpenSearch ✅ / Bedrock ⏳AWS)** | 현 pgvector는 개발용 | OpenSearch INTEGRATED P@3 1.000(>pgvector 0.917) | 대 |
| 7 | RRF/FTS 튜닝 | k=60 고정, 한국어 FTS 부재 | 융합 품질 | 소~중 |

---

## 1. MO↔KS 코드 어휘 정렬 ✅ 완료 (2026-06-14)
- **문제**: 통합 ablation에서 MO가 코드값을 돌려준 질의는 12개 중 5개(coverage **0.42**). 순수 코드 표면형("홀드"·"캔슬"·"대기")은 Term으로 확장되거나 Term컬럼과 무관해 `/resolve`가 코드값을 안 내려줬다.
- **수행**: MO `ResolveService` 의 코드값 해석을 **잔여 질의의 모든 토큰**으로 확장하고(`resolveCodeCandidates`), 용어 매핑 컬럼이 아닌 코드값도 **직접 columnMapping으로 emit**(`buildColumnMappings` 경로(2)). BASELINE 항등성·재현율 임계값(microRecall≥0.95 등) 무회귀 확인. 커밋 MO.
- **결과(E2E 재측정)**: coverage **0.42→0.58**, INTEGRATED P@3 **0.833→0.917**, **비정형 P@3 0.667→0.833**("대기" 등 코드 표면형이 패러프레이즈에 섞이면 코드 필터 추가 작동). `metadataAddsOverVector` 마진 +0.417로 확대.
- **남은 정렬 과제**: KS 문서 태그 어휘(`merchant_grade`/`payout_rule`/`adjustment_type`)는 아직 MO 코드 사전과 미정렬 → 공유 코드 사전화는 후속(데이터 거버넌스).

## 2. 쿼리 라우팅 — 선택적 MO 호출
- **문제**: 현재 `KnowledgeSearchService`는 매 질의에 MO `/resolve`(2-홉) + 임베딩을 항상 수행. 비정형(패러프레이즈)에선 MO 무기여(측정: META=KEYWORD)인데도 호출 → 불필요 지연·결합.
- **제안**: 경량 라우터 — 질의에 코드/식별자/날짜 신호가 있으면 MO 경유(정형 경로), 없으면 벡터 단독(비정형 경로). MO `unmapped`/coverage 신호를 라우팅 피드백으로 사용. 캐시(이미 Caffeine 존재)로 반복 질의 절감.
- **기대효과**: p95 지연↓, MO 장애 시 비정형 검색 영향 격리, 구조의 역할 경계 명확화.

## 3. 문서 청킹 ☑ 구현·측정 완료(2026-06-14) — null 결과
- **수행**: `knowledge_chunk` 테이블(Flyway V3) + `TextChunker`(문장 청크, 제목은 별도 청크) + `PostgresKnowledgeRecordRepositoryImpl` 의 청크-aware 벡터 arm(문서의 최상 청크로 랭킹) + `search.chunking.enabled` 토글. 적재 시 청크별 임베딩.
- **측정(A/B, 장문 코퍼스 80문서×~11문장)**: 통째 vs 청크 → **동일**(VECTOR 0.417, INTEGRATED 비정형 0.667). 청킹이 개선 못 함.
- **진단**: 병목은 *희석*이 아니라 상태 구분 문장이 보일러플레이트와 어휘를 공유해 **의미 신호가 약함** → granularity로 못 가른다. (제네릭 제목 prepend는 오히려 악화 → 교정.)
- **결론**: 기본 OFF 유지. 관련 정보가 국소·변별적인 진짜 장문 이질 문서에서 값을 함(본 합성 코퍼스는 아님). 측정 우선 — 켜기 전 데이터로 검증.

## 4. 리랭킹 ✅ 완료 (2026-06-14)
- **수행**: `Reranker` 도메인 포트 + `LlmJudgeReranker`(Ollama EXAONE 3.5 2.4B, 리스트와이즈 [번호] 재정렬) + `NoOpReranker`(기본) + 평가 RERANKED arm. `llmrerank` 프로파일에서만 활성, 질의당 1회 호출.
- **결과(E2E, 장문 코퍼스)**: 비정형 P@3 **0.667→0.833**, 전체 **0.833→0.917**, MRR 0.854→0.917, nDCG 0.833→0.917. `rerankImprovesUnstructured=true`.
- **교훈**: 리랭커 snippet에 *구별 정보*가 들어와야 함(앞부분 공통 보일러플레이트면 짧으면 무효 → 800자). 장문 문서는 #3 청킹으로 관련 청크만 리랭커에 주는 조합이 자연스러움(시너지).
- 비용: LLM 호출(질의당 1회) — 지연/비용 trade-off. 운영은 cross-encoder(예: bge-reranker) 서빙으로 대체 검토.

## 5. 평가 상시화 (회귀 게이트) ✅ 완료 (2026-06-14)
- **수행**: `scripts/eval-gate.sh` — 실행 중 KS/MO 엔드포인트의 핵심 임계값을 검사하고 회귀 시 비0 종료.
  검사: MO full microRecall≥0.95·fuzzy≥full, KS integrated 정형 P@3==1.0·metadataAddsOverVector==true·reranked 전체 P@3≥integrated. (정상→PASS, 회귀→FAIL 로직 검증 완료.)
- **운영(나이틀리/수동)**: 브링업(docs/evaluation-hybrid.md: compose pgvector ×2 → ollama+모델 → MO(postgres,local) → KS(postgres,localmodel[,llmrerank],METADATA_ENABLED) → /etl/run) 후 `bash scripts/eval-gate.sh`.
- **한계/후속**: 전체 E2E(도커+Ollama+2앱+1.2GB 모델)는 GH Actions 표준 러너에 부적합 → 로컬/전용 러너 나이틀리. 골드셋을 운영 질의 로그(`search_log`)에서 주기 보강 권장.

## 6. 운영 경로 (OpenSearch ✅ 완료 / Bedrock ⏳ AWS 대기) (2026-06-14)
- **수행(OpenSearch, 로컬 Docker, AWS 불필요)**: `OpenSearchKnowledgeRecordRepositoryImpl`(@Profile `opensearch`) — BM25+k-NN 두 질의 + Java RRF(k=60) 융합 + 정형 필터(term). 인덱스 매핑(knn_vector dim 1024) 자동 생성, 적재 시 색인. 프로파일 `postgres,opensearch`(Postgres=SearchLog/배치).
- **결과(E2E, large 코퍼스)**: OpenSearch INTEGRATED **P@3 1.000**(정형 1.000·비정형 1.000) > pgvector 0.917. BM25 가 한국어 패러프레이즈를 LIKE 보다 훨씬 잘 매칭(KEYWORD 비정형 0.333→1.000). SQL RRF→OpenSearch 1:1 이관 확인.
- **남은 부분(Bedrock)**: 임베딩 `BedrockEmbeddingProvider`(Titan v2) 구현 완료 — AWS 자격증명 확보 시 `bedrock` 프로파일로 활성(차원 1024 정렬 유지로 재인덱싱 최소). 운영 RRF 는 OpenSearch 네이티브 hybrid 파이프라인으로 교체 가능(코드 구조 동일).

## 7. RRF/FTS 튜닝
- **문제**: RRF k=60 고정, Postgres 한국어 형태소 FTS 미사용(키워드 arm은 LIKE/ILIKE).
- **제안**: RRF k·arm 가중을 골드셋으로 튜닝. 한국어 형태소 분석기(예: mecab-ko, OpenSearch nori) 도입 시 키워드 arm 품질↑. pgvector HNSW `ef_search` 튜닝.
- **기대효과**: 융합 품질 미세 개선.

---

## 결론 / 진행 상황
정밀도 압력 재측정으로 **KS→MO 참조 구조의 실효성(정형 정밀도 1.000)이 입증**됐고, **#1 어휘 정렬 완료**로 적용 범위를 넓혀 INTEGRATED P@3 0.833→0.917·비정형 0.667→0.833까지 끌어올렸다.
진행 결과 누적: **#1 어휘 정렬 ✅**(coverage 0.42→0.58, INTEGRATED 0.833→0.917@short corpus) + **#4 리랭킹 ✅**(장문 코퍼스 비정형 0.667→0.833, 전체 0.833→0.917) → 정형은 MO 코드 필터로 1.000, 비정형은 리랭킹으로 0.833. **#3 청킹 ☑측정완료(null)**(합성 코퍼스 한계, 장문 이질문서용 보관).
누적 완료: **#1·#4·#5·#6(OpenSearch) ✅**, #3 ☑측정완료(null). 현재 pgvector 경로 P@3 0.917 / OpenSearch 경로 **1.000**.
- **남은 항목**: #6 Bedrock(AWS 자격증명 대기, 코드 준비됨) · #2 라우팅(품질 무영향 지연 최적화) · #7 RRF/FTS 튜닝.
- **보류 근거**: Bedrock=AWS 자격증명 필요. #2/#7 은 품질 영향이 작아 후순위(측정 근거). **다음 큰 레버는 실데이터 평가셋** — 합성 코퍼스의 한계가 #3에서 드러남(진짜 운영 문서/질의 로그로 #3·리랭킹의 실가치 재측정 필요).
