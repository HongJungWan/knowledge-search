package com.hris.knowledgesearch.domain.knowledge;

import com.hris.knowledgesearch.domain.knowledge.vo.Body;
import com.hris.knowledgesearch.domain.knowledge.vo.CodeValues;
import com.hris.knowledgesearch.domain.knowledge.vo.ContentHash;
import com.hris.knowledgesearch.domain.knowledge.vo.KnowledgeDomain;
import com.hris.knowledgesearch.domain.knowledge.vo.SourceUrl;
import com.hris.knowledgesearch.domain.knowledge.vo.Title;
import com.hris.knowledgesearch.global.common.BaseEntity;
import com.hris.knowledgesearch.shared.ddd.AggregateRoot;
import com.hris.knowledgesearch.shared.ddd.Subdomain;
import com.hris.knowledgesearch.shared.ddd.SubdomainType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;

/**
 * 지식 레코드 (정형 사내 지식 한 건). 애그리거트 루트.
 * <p>
 * 운영 환경에서는 Redshift 네이티브 테이블 {@code knowledge_record} 에 매핑되며 읽기 위주로 사용한다(PRD §3.1, §4.3).
 * 쓰기는 ETL/배치 경로로 모은다.
 */
@AggregateRoot
@Subdomain(SubdomainType.CORE)
@Entity
@Table(name = "knowledge_record",
        indexes = {
                @Index(name = "idx_knowledge_record_domain", columnList = "domain"),
                @Index(name = "uk_knowledge_record_content_hash", columnList = "content_hash", unique = true)
        })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@ToString
public class KnowledgeRecord extends BaseEntity {

    /**
     * 지식 레코드 ID (PK).
     * <p>
     * H2/로컬에서는 JPA IDENTITY 자동 증분을 쓴다. 운영 Redshift 도 IDENTITY 지만 값의 연속성을
     * 보장하지 않고 생성 키 회수(getGeneratedKeys)를 지원하지 않으므로(PRD §3.1), 애플리케이션은
     * 키 연속성·생성 키 회수에 의존하지 않는다 — 중복 판정은 content_hash, 적재 INSERT 는 id 생략
     * ({@code RedshiftKnowledgeRecordRepositoryImpl}).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** 도메인 (예: SETTLEMENT) */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "domain", nullable = false, length = 100))
    private KnowledgeDomain domain;

    /** 제목 */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "title", nullable = false, length = 500))
    private Title title;

    /** 본문 (원문, CLOB) */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "body", nullable = false))
    private Body body;

    /** 출처 링크/식별자 (출처가 있으면 응답에 항상 붙인다, PRD §5.3/§8) */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "source_url", length = 1000))
    private SourceUrl sourceUrl;

    /**
     * 코드값 묶음.
     * <p>
     * 운영 Redshift 에서는 반정형 타입 {@code SUPER} 컬럼이다(PRD §4.3). H2 에서는 JSON 텍스트로 보관한다.
     * 예: {@code {"settlement_status":"PENDING"}}
     */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "code_values", length = 2000))
    private CodeValues codeValues;

    /** 소스 기준 콘텐츠 최종 수정 시각 (랭킹의 최신순 가중에 사용, PRD §6) */
    @Column(name = "source_updated_at")
    private Instant sourceUpdatedAt;

    /** 콘텐츠 해시 (SHA-256). 중복 제거(insert-or-skip)에 사용한다(PRD §4.3/§7). */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "content_hash", nullable = false, length = 64))
    private ContentHash contentHash;

    /**
     * 적재(ETL/배치) 경로용 팩토리. 도메인 불변식을 생성 시점에 강제한다.
     * <p>
     * 정규화/해시 계산은 호출자(ETL)가 끝낸 값을 받는다. 여기서는 무결성만 보장한다:
     * domain/title/body 비어있지 않음, contentHash 가 64자리 16진 해시.
     *
     * @throws IllegalArgumentException 불변식 위반 시
     */
    public static KnowledgeRecord forIngestion(String domain, String title, String body, String sourceUrl,
                                               String codeValues, Instant sourceUpdatedAt, String contentHash) {
        return KnowledgeRecord.builder()
                .domain(new KnowledgeDomain(domain))
                .title(new Title(title))
                .body(new Body(body))
                .sourceUrl(sourceUrl == null ? null : new SourceUrl(sourceUrl))
                .codeValues(codeValues == null ? null : new CodeValues(codeValues))
                .sourceUpdatedAt(sourceUpdatedAt)
                .contentHash(new ContentHash(contentHash))
                .build();
    }

    /** 주어진 도메인에 속하는 레코드인지. */
    public boolean belongsTo(String domain) {
        return this.domain != null && this.domain.value().equals(domain);
    }

    /**
     * 코드값 묶음에 {@code "key":"value"} 가 들어 있는지.
     * <p>
     * 코드값 일치 검색(PRD §6)의 판정을 엔티티에 캡슐화한다. 운영 Redshift 의 SUPER 경로에서도
     * 의미는 동일하다(여기선 JSON 텍스트 기준).
     */
    public boolean hasCodeValue(String key, String value) {
        return codeValues != null && codeValues.containsPair(key, value);
    }

    /** 콘텐츠 해시가 같은(=동일 내용) 레코드인지. 적재 단계 중복 판정에 쓴다. */
    public boolean hasSameContentAs(String otherContentHash) {
        return contentHash != null && contentHash.value().equals(otherContentHash);
    }
}
