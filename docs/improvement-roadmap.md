# 개선 로드맵 — knowledge-search × metadata-ontology

> 근거: `docs/evaluation-hybrid.md`(정밀도 압력 재측정), `metadata-ontology/docs/evaluation-recall.md`.
> 핵심 측정 사실: 정밀도 압력 하에서 **INTEGRATED P@3=0.833**(정형 1.000) > VECTOR 0.500 > KEYWORD 0.333.
> MO 코드 필터가 정형 정밀도를 0.333→1.000으로 끌어올림(구조 실효성 확인). 단 **MO 코드 해석 coverage=0.42**가 상한.

## 우선순위 요약
| # | 개선 | 문제(근거) | 기대효과 | 노력도 |
|---|---|---|---|---|
| 1 | MO↔KS 코드 어휘 정렬 | coverage 0.42 — 절반만 코드 해석 | 정형 정밀도 적용 범위↑ | 중 |
| 2 | 쿼리 라우팅(선택적 MO 호출) | 매 질의 2-홉(비정형엔 무기여) | 지연·비용↓, 구조 명확화 | 중 |
| 3 | 문서 청킹 | 장문 본문 통째 임베딩 | 비정형 P@3(0.667) 보강 | 중 |
| 4 | 리랭킹(cross-encoder) | 비정형 정밀도 미완 | top-k 정밀도↑ | 중 |
| 5 | 평가 상시화(회귀 게이트) | 측정이 수동 | 회귀 방지·튜닝 근거 | 소 |
| 6 | 운영 경로(Bedrock+OpenSearch) | 현 pgvector는 개발용 | 운영 확장성 | 대 |
| 7 | RRF/FTS 튜닝 | k=60 고정, 한국어 FTS 부재 | 융합 품질 | 소~중 |

---

## 1. MO↔KS 코드 어휘 정렬 (최우선)
- **문제**: 통합 ablation에서 MO가 코드값을 돌려준 질의는 12개 중 5개(coverage **0.42**). 정형 질의의 일부 표면형(예: "보류" 단독)은 Term 동의어가 아니라 `/resolve`가 코드값을 안 내려준다. 또 KS 문서 태그 어휘(`merchant_grade`/`payout_rule`/`adjustment_type` 등)는 MO 매핑과 거의 안 겹친다(직전 소형 코퍼스 coverage 0.083의 원인).
- **제안**: (a) KS 문서 `code_values` 키·값을 MO 코드값 사전(`code_values.csv`)·동의어와 **공유 코드 사전**으로 정렬. (b) 순수 코드 표면형("보류")도 `/resolve`가 코드로 내려주도록 MO `resolveCodeValues`를 동의어 확장 외 **표면형 직매칭**까지 확장(현재는 `/match-sql-pattern` 분리). (c) 정렬 커버리지를 평가 지표(`metadataCodeValueCoverage`)로 상시 추적.
- **기대효과**: 코드 필터가 적용되는 정형 질의 비율↑ → INTEGRATED 정형 정밀도(이미 1.000)의 **적용 범위** 확대. 구조의 ROI 직접 증가.

## 2. 쿼리 라우팅 — 선택적 MO 호출
- **문제**: 현재 `KnowledgeSearchService`는 매 질의에 MO `/resolve`(2-홉) + 임베딩을 항상 수행. 비정형(패러프레이즈)에선 MO 무기여(측정: META=KEYWORD)인데도 호출 → 불필요 지연·결합.
- **제안**: 경량 라우터 — 질의에 코드/식별자/날짜 신호가 있으면 MO 경유(정형 경로), 없으면 벡터 단독(비정형 경로). MO `unmapped`/coverage 신호를 라우팅 피드백으로 사용. 캐시(이미 Caffeine 존재)로 반복 질의 절감.
- **기대효과**: p95 지연↓, MO 장애 시 비정형 검색 영향 격리, 구조의 역할 경계 명확화.

## 3. 문서 청킹
- **문제**: `PostgresKnowledgeRecordRepositoryImpl.save`가 `title+body` 전체를 1벡터로 임베딩. 실제 장문 문서는 토픽이 희석돼 비정형 정밀도 손해(측정: 비정형 P@3 0.667로 미완).
- **제안**: 본문을 청크(문단/슬라이딩 윈도)로 나눠 청크별 임베딩·색인, 검색 시 청크→문서 집계(max/평균). `knowledge_record` 1:N `knowledge_chunk(embedding)` 테이블(Flyway V3).
- **기대효과**: 비정형 retrieval granularity·정밀도↑. 운영 장문 문서에서 특히.

## 4. 리랭킹
- **문제**: RRF 융합 top-k는 1차 후보. 비정형 정밀도가 1.0 미만.
- **제안**: top-k(예: 20)를 cross-encoder(또는 LLM judge)로 재정렬해 최종 k 산출. `EmbeddingProvider`처럼 `Reranker` 포트 + 로컬/Bedrock 구현.
- **기대효과**: 비정형 P@k↑(중복·근접 오답 제거). 비용 vs 품질 트레이드오프는 평가로 결정.

## 5. 평가 상시화 (회귀 게이트)
- **문제**: 본 ablation·정밀도 측정이 수동(REST). 회귀를 자동으로 못 잡음.
- **제안**: 대량 코퍼스 + precision@k + 코드 relevance를 `@Tag("semantic")` CI 잡(도커 pgvector + 임베딩)으로 주기 실행, `metadataAddsOverVector`·정형 P@k 임계 회귀 시 알림. 골드셋을 운영 질의 로그(`search_log`)에서 주기 보강.
- **기대효과**: 어휘 정렬·튜닝 변경의 효과를 수치로 게이팅.

## 6. 운영 경로 (Bedrock + OpenSearch)
- **문제**: 현 pgvector/Postgres는 개발·측정 차량. KS 운영은 Redshift(pgvector 불가).
- **제안**: 임베딩 `BedrockEmbeddingProvider`(Titan v2, 이미 구현) 활성, 검색은 OpenSearch(BM25+kNN **네이티브 RRF**)로 4번째 어댑터 추가 — 본 SQL RRF가 1:1 이식. MO는 Postgres+pg_trgm로 운영 가능(이미 검증).
- **기대효과**: 운영 규모 확장성·관리형 인프라. 차원(1024) 정렬 유지로 재인덱싱 최소.

## 7. RRF/FTS 튜닝
- **문제**: RRF k=60 고정, Postgres 한국어 형태소 FTS 미사용(키워드 arm은 LIKE/ILIKE).
- **제안**: RRF k·arm 가중을 골드셋으로 튜닝. 한국어 형태소 분석기(예: mecab-ko, OpenSearch nori) 도입 시 키워드 arm 품질↑. pgvector HNSW `ef_search` 튜닝.
- **기대효과**: 융합 품질 미세 개선.

---

## 결론
정밀도 압력 재측정으로 **KS→MO 참조 구조의 실효성(정형 정밀도 1.000)이 입증**됐다. 다음 한 수는 **#1 어휘 정렬**로 그 효과의 적용 범위를 넓히고, **#2 라우팅**으로 비정형 경로의 군더더기를 덜어내는 것이다. #3·#4는 비정형 정밀도(0.667)를 끌어올리는 직교 개선, #5는 이 모든 변경을 수치로 지키는 안전망이다.
