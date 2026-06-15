package com.hris.knowledgesearch.domain.knowledge;

import com.hris.knowledgesearch.shared.ddd.ValueObject;

/** 한 계층(정형/비정형/전체)의 평가 점수 — 도메인이 소유하는 순수 값 객체. */
@ValueObject
public record StratumScore(double precisionAtK, double recallAtK) {
}
