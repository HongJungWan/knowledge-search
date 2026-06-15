package com.hris.knowledgesearch.domain.knowledge.vo;

import com.hris.knowledgesearch.shared.ddd.ValueObject;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;

/** 정성 평가 점수(0~10) 값 객체 — 미평가 시 엔티티 필드가 null. */
@Embeddable
@ValueObject
@EqualsAndHashCode
public final class JudgedScore {

    private final int value;

    protected JudgedScore() {
        this.value = 0;
    }

    public JudgedScore(int value) {
        if (value < 0 || value > 10) {
            throw new IllegalArgumentException("judgedScore 는 0~10 이어야 합니다: " + value);
        }
        this.value = value;
    }

    public int value() {
        return value;
    }

    /** 임계 점수 미만인지. */
    public boolean isBelow(int minScore) {
        return value < minScore;
    }
}
