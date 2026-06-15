package com.hris.knowledgesearch.domain.knowledge.vo;

import com.hris.knowledgesearch.shared.ddd.ValueObject;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;

/** 검색 지연 시간(ms) 값 객체 — 0 이상 불변식. */
@Embeddable
@ValueObject
@EqualsAndHashCode
public final class Latency {

    private final long millis;

    protected Latency() {
        this.millis = 0L;
    }

    public Latency(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("latencyMs 는 0 이상이어야 합니다: " + millis);
        }
        this.millis = millis;
    }

    public long millis() {
        return millis;
    }
}
