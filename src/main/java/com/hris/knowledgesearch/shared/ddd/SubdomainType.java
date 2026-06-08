package com.hris.knowledgesearch.shared.ddd;

/**
 * 서브도메인 유형 (DDD 전략 설계).
 * <ul>
 *   <li>{@link #CORE} — 경쟁 우위가 걸린 핵심. 가장 많은 설계 노력을 들인다.</li>
 *   <li>{@link #SUPPORTING} — 핵심을 돕지만 차별화 요소는 아닌 보조.</li>
 *   <li>{@link #GENERIC} — 범용(외부 솔루션으로 대체 가능). 이 프로젝트엔 현재 없음.</li>
 * </ul>
 */
public enum SubdomainType {
    CORE,
    SUPPORTING,
    GENERIC
}
