package com.hris.knowledgesearch.domain.knowledge.vo;

import com.hris.knowledgesearch.shared.ddd.ValueObject;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;

/**
 * 코드값 묶음 값 객체 (JSON 텍스트, nullable).
 * <p>
 * 운영 Redshift 는 SUPER, H2/Postgres 는 JSON 텍스트/JSONB. 코드값 일치 판정({@link #containsPair})을
 * 캡슐화한다. QueryDSL/SQL 은 {@code .codeValues.value} 로 텍스트 contains·jsonb 바인딩.
 */
@Embeddable
@ValueObject
@EqualsAndHashCode
public final class CodeValues {

    private final String value;

    protected CodeValues() {
        this.value = null;
    }

    public CodeValues(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** 코드값 묶음에 {@code "key":"value"} 가 들어 있는지(JSON 텍스트 기준). */
    public boolean containsPair(String key, String value) {
        if (this.value == null || key == null || value == null) {
            return false;
        }
        return this.value.contains("\"" + key + "\":\"" + value + "\"");
    }
}
