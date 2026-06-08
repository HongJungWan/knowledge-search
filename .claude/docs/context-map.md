# Context Map — knowledge-search (ks)

DDD 전략 설계 관점에서 ks 가 인접 컨텍스트와 맺는 관계를 정리한다. 상세 PRD 는 `prd-knowledge-search.md`.

## 바운디드 컨텍스트

| 컨텍스트 | 역할 | ks 와의 관계 |
|---|---|---|
| **knowledge-search (ks)** | 정형 사내 지식(SETTLEMENT) 검색 + MCP 서버 | 본 컨텍스트 |
| **metadata-ontology** | 자연어 질의 → 표준 용어·물리 컬럼/코드값·기간 해석 | upstream(공급자). ks 가 ACL 로 소비 |
| **외부 정산 소스(Settlement Source)** | 외부에서 들어오는 원본 정산 지식(JSON, 비정규) | upstream. ETL 이 ACL 로 흡수 |

## 관계 다이어그램

```
                    metadata-ontology (upstream)
                            │  POST /api/resolve
                            ▼  (ACL: 포트+어댑터)
   외부 정산 소스 ──ETL──▶  knowledge-search (ks, CORE)
   (JSON, 비정규)   (ACL)        │
                                 ├─ KnowledgeRecord  [CORE]
                                 └─ SearchLog        [SUPPORTING]
```

## ACL 경계 (anti-corruption)

### 1. metadata-ontology 컨텍스트
- **포트**: `application.knowledge.port.MetadataResolvePort` (`resolve(query)` → `MetadataResolveResult`).
- **어댑터**: `infrastructure.metadata.MetadataClient` (RestClient, `metadata.enabled` 플래그 뒤, 기본 false).
- **번역**: 외부 응답(`ResolveResponse`)을 `MetadataResolveResult` 로만 받아 응용/도메인으로 외부 모델이 새지 않게 한다. 비활성·실패는 원본 질의 폴백(`MetadataResolveResult.raw`)으로 흡수 → 검색은 metadata 없이도 동작.
- **관계 유형**: Customer/Supplier + Conformist 회피용 ACL. ks 는 metadata 스키마 변화로부터 보호된다.

### 2. 외부 정산 소스 컨텍스트 (ETL)
- **포트**: `application.knowledge.port.SettlementSourceAcl` (`toIngestCommand(...)` → `Optional<IngestKnowledgeCommand>`).
- **어댑터**: `infrastructure.etl.SettlementSourceAclAdapter` — 정규화(`TextNormalizer`) · SHA-256 해시(`HashUtil`) · 도메인 기본값(`SETTLEMENT`) · 필수 필드 검증(누락 시 `Optional.empty()` = skip).
- **흐름**: `IngestionJobConfig` 프로세서가 외부 `SettlementSourceItem` 을 ACL 에 넘겨 `IngestKnowledgeCommand` 를 받고, `KnowledgeRecord.forIngestion(...)` 으로 도메인 무결성을 재확인한 뒤 적재한다. 외부 표현(공백/대소문자/중복)이 도메인으로 새지 않는다.
- **관계 유형**: upstream 원본 → ACL 번역. 외부 JSON 형태 변화가 도메인을 오염시키지 못하게 차단.

## 서브도메인

| 서브도메인 | 타입 | 비고 |
|---|---|---|
| 지식 검색(KnowledgeRecord) | CORE | 검색 랭킹·코드값 매칭이 경쟁 우위 |
| 관측성(SearchLog) | SUPPORTING | 사전 보강 분석 원천 |
| (없음) | GENERIC | 현재 부재 |

ArchUnit `CORE_NOT_DEPEND_ON_GENERIC` 규칙으로 CORE→GENERIC 의존을 금지(현재 GENERIC 부재로 vacuous).
