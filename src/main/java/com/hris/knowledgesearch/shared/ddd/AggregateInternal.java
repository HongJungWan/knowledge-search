package com.hris.knowledgesearch.shared.ddd;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 애그리거트 내부 엔티티. 같은 패키지(= 같은 애그리거트) 밖에서 직접 접근 금지. AR 통해서만 도달. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AggregateInternal {
}
