package com.hris.knowledgesearch.domain.knowledge.vo;

import com.hris.knowledgesearch.shared.ddd.ValueObject;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;

import java.util.regex.Pattern;

/** 콘텐츠 해시 값 객체 — 64자리 16진 SHA-256 형식 불변식. 적재 중복 판정(insert-or-skip)에 사용. */
@Embeddable
@ValueObject
@EqualsAndHashCode
public final class ContentHash {

    private static final Pattern PATTERN = Pattern.compile("^[0-9a-fA-F]{64}$");

    private final String value;

    protected ContentHash() {
        this.value = null;
    }

    public ContentHash(String value) {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("contentHash 는 64자리 16진 SHA-256 이어야 합니다: " + value);
        }
        this.value = value;
    }

    public String value() {
        return value;
    }
}
