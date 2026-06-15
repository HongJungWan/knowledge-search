package com.hris.knowledgesearch.domain.knowledge;

import com.hris.knowledgesearch.domain.knowledge.vo.HitCount;
import com.hris.knowledgesearch.domain.knowledge.vo.JudgedScore;
import com.hris.knowledgesearch.domain.knowledge.vo.Latency;
import com.hris.knowledgesearch.domain.knowledge.vo.NormalizedQuery;
import com.hris.knowledgesearch.domain.knowledge.vo.RawQuery;
import com.hris.knowledgesearch.global.common.BaseEntity;
import com.hris.knowledgesearch.shared.ddd.AggregateRoot;
import com.hris.knowledgesearch.shared.ddd.Subdomain;
import com.hris.knowledgesearch.shared.ddd.SubdomainType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 검색 호출 로그 (PRD §9 관측성). 애그리거트 루트.
 * <p>
 * 질의·정규화 결과·도구·지연·적중 수·평가 점수를 남긴다. 매핑 안 된 키워드/저점수 질의를
 * 모아 metadata 사전 보강으로 넘기는 분석의 원천이다.
 */
@AggregateRoot
@Subdomain(SubdomainType.SUPPORTING)
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
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "query_raw", length = 2000))
    private RawQuery queryRaw;

    /** 정규화된 질의 (metadata /resolve 의 normalizedQuery) */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "query_normalized", length = 2000))
    private NormalizedQuery queryNormalized;

    /** 호출된 MCP 도구 (SEARCH_KNOWLEDGE / GET_RECORD — list_schema 는 현재 미기록) */
    @Enumerated(EnumType.STRING)
    @Column(name = "tool", length = 100)
    private ToolName tool;

    /** 지연 시간 (ms) */
    @Embedded
    @AttributeOverride(name = "millis", column = @Column(name = "latency_ms"))
    private Latency latencyMs;

    /** 적중 레코드 수 */
    @Embedded
    @AttributeOverride(name = "count", column = @Column(name = "hit_count"))
    private HitCount hitCount;

    /** 정성 평가 점수 (0~10, 미평가 시 null) */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "judged_score"))
    private JudgedScore judgedScore;

    /**
     * 검색 호출 로그 생성 팩토리. 도메인 불변식을 생성 시점에 강제한다.
     * <p>
     * queryNormalized 는 null 허용(정규화 미적용 호출 경로). judgedScore 는 사후 평가이므로 생성 시 null.
     *
     * @throws IllegalArgumentException queryRaw 공백 / latencyMs<0 / hitCount<0
     */
    public static SearchLog record(String queryRaw, String queryNormalized, ToolName tool,
                                   long latencyMs, int hitCount) {
        return SearchLog.builder()
                .queryRaw(new RawQuery(queryRaw))
                .queryNormalized(queryNormalized == null ? null : new NormalizedQuery(queryNormalized))
                .tool(tool)
                .latencyMs(new Latency(latencyMs))
                .hitCount(new HitCount(hitCount))
                .build();
    }

    /** 적중(검색 결과 1건 이상)했는지. */
    public boolean isHit() {
        return hitCount != null && hitCount.isHit();
    }

    /**
     * metadata 사전 보강 후보인지(PRD §9).
     * <p>
     * 적중이 없거나 평가 점수가 임계 미만이면, 매핑/동의어 보강 대상으로 본다.
     */
    public boolean needsDictionaryReinforcement(int minScore) {
        return !isHit() || (judgedScore != null && judgedScore.isBelow(minScore));
    }
}
