package com.hris.knowledgesearch.domain.knowledge.vo;

import com.hris.knowledgesearch.shared.ddd.ValueObject;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;

/**
 * 지식 도메인 분류 값 객체 (예: SETTLEMENT).
 * <p>
 * 원시 String 포장 — 비어있지 않음 불변식을 생성 시점에 강제한다. {@code @Embeddable} 단일 필드라
 * QueryDSL 은 {@code .domain.value}(StringPath)로 접근하고, {@code @AttributeOverride} 로 DB 컬럼명은
 * {@code domain} 그대로 유지된다.
 * <p>
 * record 가 아니라 final 필드 클래스로 둔다 — QueryDSL 5.1.0 APT 가 record {@code @Embeddable} 의
 * Q타입 생성을 지원하지 않기 때문(Hibernate 6.6 는 양쪽 다 지원). 접근자는 record 와 동일하게 {@code value()}.
 */
@Embeddable
@ValueObject
@EqualsAndHashCode
public final class KnowledgeDomain {

    private final String value;

    /** JPA 전용. */
    protected KnowledgeDomain() {
        this.value = null;
    }

    public KnowledgeDomain(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("domain 은 비어있을 수 없습니다");
        }
        this.value = value;
    }

    public String value() {
        return value;
    }
}
