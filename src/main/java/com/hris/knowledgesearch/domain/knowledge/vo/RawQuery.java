package com.hris.knowledgesearch.domain.knowledge.vo;

import com.hris.knowledgesearch.shared.ddd.ValueObject;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;

/** 검색 원본 질의 값 객체 — 비어있지 않음 불변식. */
@Embeddable
@ValueObject
@EqualsAndHashCode
public final class RawQuery {

    private final String value;

    protected RawQuery() {
        this.value = null;
    }

    public RawQuery(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("queryRaw 는 비어있을 수 없습니다");
        }
        this.value = value;
    }

    public String value() {
        return value;
    }
}
