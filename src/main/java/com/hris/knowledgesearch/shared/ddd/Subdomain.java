package com.hris.knowledgesearch.shared.ddd;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 서브도메인 분류 마커 (DDD 전략 설계). 애그리거트/도메인 타입이 어떤 서브도메인에 속하는지 표시한다.
 * <p>
 * 분류는 설계 의도를 코드에 고정하고, ArchUnit 규칙(예: CORE 가 GENERIC 에 의존 금지)의 근거가 된다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Subdomain {
    SubdomainType value();
}
