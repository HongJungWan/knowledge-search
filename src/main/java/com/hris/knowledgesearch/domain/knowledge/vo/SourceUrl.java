package com.hris.knowledgesearch.domain.knowledge.vo;

import com.hris.knowledgesearch.shared.ddd.ValueObject;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;

/**
 * 출처 링크/식별자 값 객체 (nullable — 출처 없는 레코드 허용). 값이 있으면 공백 불가.
 * 엔티티에서 null 이면 임베디드 전체가 null 이다.
 */
@Embeddable
@ValueObject
@EqualsAndHashCode
public final class SourceUrl {

    private final String value;

    protected SourceUrl() {
        this.value = null;
    }

    public SourceUrl(String value) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException("sourceUrl 은 공백일 수 없습니다(없으면 null)");
        }
        this.value = value;
    }

    public String value() {
        return value;
    }
}
