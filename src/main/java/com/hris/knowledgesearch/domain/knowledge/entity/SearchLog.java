package com.hris.knowledgesearch.domain.knowledge.entity;

import com.hris.knowledgesearch.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 검색 호출 로그 (PRD §9 관측성).
 * <p>
 * 질의·정규화 결과·도구·지연·적중 수·평가 점수를 남긴다. 매핑 안 된 키워드/저점수 질의를
 * 모아 metadata 사전 보강으로 넘기는 분석의 원천이다.
 */
@Entity
@Table(name = "search_log")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@ToString
public class SearchLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** 원본 질의 */
    @Column(name = "query_raw", length = 2000)
    private String queryRaw;

    /** 정규화된 질의 (metadata /resolve 의 normalizedQuery) */
    @Column(name = "query_normalized", length = 2000)
    private String queryNormalized;

    /** 호출된 MCP 도구명 (search_knowledge / get_record / list_schema) */
    @Column(name = "tool", length = 100)
    private String tool;

    /** 지연 시간 (ms) */
    @Column(name = "latency_ms")
    private Long latencyMs;

    /** 적중 레코드 수 */
    @Column(name = "hit_count")
    private Integer hitCount;

    /** 정성 평가 점수 (0~10, 미평가 시 null) */
    @Column(name = "judged_score")
    private Integer judgedScore;
}
