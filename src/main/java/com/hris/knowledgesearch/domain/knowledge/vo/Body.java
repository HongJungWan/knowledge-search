package com.hris.knowledgesearch.domain.knowledge.vo;

import com.hris.knowledgesearch.shared.ddd.ValueObject;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Lob;
import lombok.EqualsAndHashCode;

/**
 * 지식 레코드 본문 값 객체 — 비어있지 않음 불변식. CLOB(@Lob) 매핑.
 * QueryDSL {@code .body.value}(StringPath, 대소문자 구분 contains).
 */
@Embeddable
@ValueObject
@EqualsAndHashCode
public final class Body {

    @Lob
    private final String value;

    protected Body() {
        this.value = null;
    }

    public Body(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("body 는 비어있을 수 없습니다");
        }
        this.value = value;
    }

    public String value() {
        return value;
    }
}
