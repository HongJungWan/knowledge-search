package com.hris.knowledgesearch.application.knowledge.port;

/**
 * metadata 서비스로 질의를 해석하는 아웃바운드 포트.
 * <p>
 * // ACL: 이 포트는 metadata 바운디드 컨텍스트에 대한 anti-corruption 경계다. metadata 의 응답 모델
 * ({@code ResolveResponse})은 {@link MetadataResolveResult} 로만 들어오고, 그 변화가 응용/도메인으로
 * 새어 들어오지 못하게 막는다(별도 레이어 불필요 — 포트+어댑터가 ACL 역할).
 * <p>
 * application 레이어는 이 포트에만 의존하고, 실제 호출(REST)은 infrastructure 어댑터
 * ({@code infra.metadata.MetadataClient})가 구현한다. 레이어 경계(application↛infra)를 지키기 위한 분리다.
 */
public interface MetadataResolvePort {

    /** 자연어 질의를 표준 용어·물리 컬럼/코드값·기간으로 해석한다. */
    MetadataResolveResult resolve(String query);
}
