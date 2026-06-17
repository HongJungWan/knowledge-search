---
name: jpa-persistence
description: JPA/QueryDSL 영속성 작업 시 로드. 리포지토리 인터페이스(도메인)와 구현(infra) 분리, 엔티티 매핑 규칙.
---

# JPA / 영속성 가이드

- **리포지토리 인터페이스(포트)는 도메인이 소유**, 구현체(`*RepositoryImpl`, QueryDSL)는 infrastructure 에 둔다(DIP).
- 실용 레이어드(이 템플릿 기본값): 도메인 엔티티의 `@Entity`/Lombok 은 허용. 단, 도메인이 `*.infra.*`·
  외부 SDK(AWS 등)·HTTP 클라이언트를 직접 import 하면 가드 훅이 차단한다.
- 더 엄격히(순수 헥사고날) 가려면 `harness.config.json` 의 `forbiddenImports.domain` 에
  `jakarta.persistence`/`org.springframework`/`lombok` 을 추가하고, 영속 모델과 도메인 모델을 분리한다.
- 복잡 조회는 QueryDSL custom 구현을 infra 에. N+1 은 fetch join / `@EntityGraph` 로.
- 애그리거트 간 참조는 **연관 객체가 아니라 식별자(ID)** 로([[ddd-guidelines]]).
- **VO 와 `record` + QueryDSL 주의:** 메모리 계산용 VO 는 `record` 가 깔끔하다. 그러나 `@Embeddable` 로 엔티티에
  넣고 QueryDSL 검색 조건으로도 쓰는 VO 는 QueryDSL 5.1.0 APT 가 record 기반 Q클래스를 생성하지 못한다.
  이 경우 record 대신 **불변 클래스**(`final` 필드 + 생성자, setter 없음)로 우회한다 — ArchUnit `DDD_VO_IMMUTABLE` 는 둘 다 통과.
