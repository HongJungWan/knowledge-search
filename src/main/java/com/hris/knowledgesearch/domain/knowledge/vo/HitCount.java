package com.hris.knowledgesearch.domain.knowledge.vo;

import com.hris.knowledgesearch.shared.ddd.ValueObject;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;

/** 적중 레코드 수 값 객체 — 0 이상 불변식. */
@Embeddable
@ValueObject
@EqualsAndHashCode
public final class HitCount {

    private final int count;

    protected HitCount() {
        this.count = 0;
    }

    public HitCount(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("hitCount 는 0 이상이어야 합니다: " + count);
        }
        this.count = count;
    }

    public int count() {
        return count;
    }

    /** 적중(1건 이상)했는지. */
    public boolean isHit() {
        return count > 0;
    }
}
