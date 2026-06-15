package com.hris.knowledgesearch.domain.knowledge;

import com.hris.knowledgesearch.shared.ddd.ValueObject;

/** 한 arm(KEYWORD/META/VECTOR/INTEGRATED/RERANKED)의 전체·계층별 점수 — 도메인 값 객체. */
@ValueObject
public record ArmScore(StratumScore overall, StratumScore structured, StratumScore unstructured) {
}
