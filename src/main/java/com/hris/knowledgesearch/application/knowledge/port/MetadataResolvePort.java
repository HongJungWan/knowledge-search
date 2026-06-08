package com.hris.knowledgesearch.application.knowledge.port;

/**
 * metadata 서비스로 질의를 해석하는 아웃바운드 포트.
 * <p>
 * application 레이어는 이 포트에만 의존하고, 실제 호출(REST)은 infrastructure 어댑터
 * ({@code infra.metadata.MetadataClient})가 구현한다. 레이어 경계(application↛infra)를 지키기 위한 분리다.
 */
public interface MetadataResolvePort {

    /** 자연어 질의를 표준 용어·물리 컬럼/코드값·기간으로 해석한다. */
    MetadataResolveResult resolve(String query);
}
