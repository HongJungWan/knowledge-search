package com.hris.knowledgesearch.domain.knowledge;

import com.hris.knowledgesearch.global.common.BaseEntity;
import com.hris.knowledgesearch.shared.ddd.AggregateRoot;
import com.hris.knowledgesearch.shared.ddd.Subdomain;
import com.hris.knowledgesearch.shared.ddd.SubdomainType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
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
     * H2/로컬에서는 IDENTITY 자동 증분을 쓴다. 단 Redshift 의 IDENTITY 는 값의 연속성을 보장하지 않으므로(PRD §3.1),
     * 운영 환경의 키는 ETL/배치에서 생성한 값을 사용한다(여기서 의존하지 않는다).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** 도메인 (예: SETTLEMENT) */
    @Column(name = "domain", nullable = false, length = 100)
    private String domain;

    /** 제목 */
    @Column(name = "title", nullable = false, length = 500)
    private String title;

    /** 본문 (원문) */
    @Lob
    @Column(name = "body", nullable = false)
    private String body;

    /** 출처 링크/식별자 (응답에 항상 붙인다, PRD §5.3/§8) */
    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    /**
     * 코드값 묶음.
     * <p>
     * 운영 Redshift 에서는 반정형 타입 {@code SUPER} 컬럼이다(PRD §4.3). H2 에서는 JSON 텍스트로 보관한다.
     * 예: {@code {"settlement_status":"PENDING"}}
     */
    @Column(name = "code_values", length = 2000)
    private String codeValues;

    /** 소스 기준 콘텐츠 최종 수정 시각 (랭킹의 최신순 가중에 사용, PRD §6) */
    @Column(name = "source_updated_at")
    private Instant sourceUpdatedAt;

    /** 콘텐츠 해시 (SHA-256). 중복 제거·변경 감지에 사용한다(PRD §4.3/§7). */
    @Column(name = "content_hash", nullable = false, length = 64, unique = true)
    private String contentHash;

    /** 콘텐츠 해시 형식(소문자/대문자 무관 64자리 16진수) 검증용. */
    private static final java.util.regex.Pattern CONTENT_HASH_PATTERN =
            java.util.regex.Pattern.compile("^[0-9a-fA-F]{64}$");

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
        if (domain == null || domain.isBlank()) {
            throw new IllegalArgumentException("domain 은 비어있을 수 없습니다");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title 은 비어있을 수 없습니다");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body 는 비어있을 수 없습니다");
        }
        if (contentHash == null || !CONTENT_HASH_PATTERN.matcher(contentHash).matches()) {
            throw new IllegalArgumentException("contentHash 는 64자리 16진 SHA-256 이어야 합니다: " + contentHash);
        }
        return KnowledgeRecord.builder()
                .domain(domain)
                .title(title)
                .body(body)
                .sourceUrl(sourceUrl)
                .codeValues(codeValues)
                .sourceUpdatedAt(sourceUpdatedAt)
                .contentHash(contentHash)
                .build();
    }

    /** 주어진 도메인에 속하는 레코드인지. */
    public boolean belongsTo(String domain) {
        return this.domain != null && this.domain.equals(domain);
    }

    /**
     * 코드값 묶음에 {@code "key":"value"} 가 들어 있는지.
     * <p>
     * 코드값 일치 검색(PRD §6)의 판정을 엔티티에 캡슐화한다. 운영 Redshift 의 SUPER 경로에서도
     * 의미는 동일하다(여기선 JSON 텍스트 기준).
     */
    public boolean hasCodeValue(String key, String value) {
        if (codeValues == null || key == null || value == null) {
            return false;
        }
        return codeValues.contains("\"" + key + "\":\"" + value + "\"");
    }

    /** 콘텐츠 해시가 같은(=동일 내용) 레코드인지. 적재 단계 중복 판정에 쓴다. */
    public boolean hasSameContentAs(String otherContentHash) {
        return contentHash != null && contentHash.equals(otherContentHash);
    }
}
