package com.hris.knowledgesearch.shared.ddd;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 애그리거트 루트(외부 진입점). 외부는 이 타입을 통해서만 애그리거트에 접근한다. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AggregateRoot {
}
