package com.hris.knowledgesearch.domain.knowledge.vo;

import com.hris.knowledgesearch.shared.ddd.ValueObject;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;

/** 정규화된 질의 값 객체 (nullable — 정규화 미적용 경로 허용). 값이 있으면 공백 불가. */
@Embeddable
@ValueObject
@EqualsAndHashCode
public final class NormalizedQuery {

    private final String value;

    protected NormalizedQuery() {
        this.value = null;
    }

    public NormalizedQuery(String value) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException("queryNormalized 는 공백일 수 없습니다(없으면 null)");
        }
        this.value = value;
    }

    public String value() {
        return value;
    }
}
