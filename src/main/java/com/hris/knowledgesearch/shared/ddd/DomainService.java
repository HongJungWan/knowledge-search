package com.hris.knowledgesearch.shared.ddd;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 도메인 서비스. 무상태(주입 의존성 외 가변 인스턴스 필드 금지)이며 복수 애그리거트 조율에 한정. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DomainService {
}
