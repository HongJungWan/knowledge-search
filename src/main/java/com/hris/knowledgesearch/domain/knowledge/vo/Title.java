package com.hris.knowledgesearch.domain.knowledge.vo;

import com.hris.knowledgesearch.shared.ddd.ValueObject;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;

/** 지식 레코드 제목 값 객체 — 비어있지 않음 불변식. QueryDSL {@code .title.value}(StringPath). */
@Embeddable
@ValueObject
@EqualsAndHashCode
public final class Title {

    private final String value;

    protected Title() {
        this.value = null;
    }

    public Title(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("title 은 비어있을 수 없습니다");
        }
        this.value = value;
    }

    public String value() {
        return value;
    }
}
