package com.hris.knowledgesearch.shared.ddd;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 도메인 이벤트. 불변이어야 하며 과거형으로 명명한다(예: OrderPlaced). */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DomainEvent {
}
