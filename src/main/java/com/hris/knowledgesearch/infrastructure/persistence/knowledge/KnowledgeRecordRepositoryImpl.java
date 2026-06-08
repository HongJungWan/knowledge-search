package com.hris.knowledgesearch.infrastructure.persistence.knowledge;

import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecord;
import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecordRepository;
import com.hris.knowledgesearch.domain.knowledge.QKnowledgeRecord;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 지식 레코드 리포지토리 포트의 어댑터 (PRD §6).
 * <p>
 * 도메인 포트({@link KnowledgeRecordRepository})를 Spring Data JPA({@link KnowledgeRecordJpaRepository})와
 * QueryDSL({@link JPAQueryFactory})로 구현한다. 자유 입력 SQL 을 받지 않는다. 허용된 조건(domain/keyword/code-value)을
 * {@link BooleanBuilder} 로만 조립한다. 랭킹은 완전일치 &gt; 코드값일치 &gt; 부분일치 순으로 점수를 매기고,
 * 그 안에서 source_updated_at 최신순으로 둔다.
 * <p>
 * TODO(AWS): 운영 Redshift 에서는 SELECT 전에 {@code EXPLAIN} 으로 구문·비용을 한 번 점검한다(PRD §6).
 *            현재 H2 경로에서는 EXPLAIN 점검을 생략한다. Redshift datasource 가 활성화되고
 *            {@code search.spectrum.enabled=true} 가 되면 가드 뒤에서 EXPLAIN 을 선행한다.
 */
@Repository
@RequiredArgsConstructor
public class KnowledgeRecordRepositoryImpl implements KnowledgeRecordRepository {

    private static final QKnowledgeRecord RECORD = QKnowledgeRecord.knowledgeRecord;

    private final KnowledgeRecordJpaRepository jpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public List<KnowledgeRecord> search(String domain, String keyword,
                                        Map<String, String> codeValues, int limit) {
        BooleanBuilder where = new BooleanBuilder();
        where.and(RECORD.deletedAt.isNull());

        if (StringUtils.hasText(domain)) {
            where.and(RECORD.domain.eq(domain));
        }

        // 키워드 매칭(토큰 OR)과 코드값 매칭(코드 AND)을 서로 OR 로 결합한다.
        // PRD §6 랭킹이 "코드값일치" 단독 결과를 전제하므로, metadata 가 확장한 표준어(예: "정산상태")가
        // 본문에 없더라도 코드값(settlement_status=PENDING)이 일치하면 결과에 포함돼야 한다.
        // 키워드 AND 코드값으로 묶으면 정밀도 과다로 유효한 코드값 매칭이 누락된다.
        BooleanBuilder match = new BooleanBuilder();

        if (StringUtils.hasText(keyword)) {
            // 정규화된 질의는 공백으로 이어진 다중 토큰일 수 있어 토큰 단위 OR 로 매칭한다.
            // body 는 @Lob(CLOB) 이라 lower() 불가 → 본문은 대소문자 구분 contains.
            // (한국어 본문은 대소문자 개념이 없어 실질 영향 없음. 운영 Redshift 는 SUPER/VARCHAR 경로.)
            BooleanBuilder keywordMatch = new BooleanBuilder();
            for (String token : keyword.trim().split("\\s+")) {
                if (token.isBlank() || isPeriodToken(token)) {
                    continue; // 기간 정규화 토큰(YYYY-MM-DD~YYYY-MM-DD)은 키워드에서 제외
                }
                keywordMatch.or(RECORD.title.containsIgnoreCase(token).or(RECORD.body.contains(token)));
            }
            if (keywordMatch.hasValue()) {
                match.or(keywordMatch);
            }
        }

        // 코드값 일치: code_values 텍스트(JSON)에 "key":"value" 형태가 포함되면 일치로 본다.
        // (H2 에서는 SUPER 가 없어 JSON 텍스트 contains 로 근사한다. 운영 Redshift 는 SUPER 경로 사용.)
        if (codeValues != null) {
            BooleanBuilder codeMatch = new BooleanBuilder();
            codeValues.forEach((k, v) -> {
                if (StringUtils.hasText(k) && StringUtils.hasText(v)) {
                    codeMatch.and(RECORD.codeValues.contains("\"" + k + "\":\"" + v + "\""));
                }
            });
            if (codeMatch.hasValue()) {
                match.or(codeMatch);
            }
        }

        if (match.hasValue()) {
            where.and(match);
        }

        return queryFactory
                .selectFrom(RECORD)
                .where(where)
                .orderBy(rankOrder(keyword, codeValues), RECORD.sourceUpdatedAt.desc().nullsLast())
                .limit(Math.max(1, limit))
                .fetch();
    }

    @Override
    public Optional<KnowledgeRecord> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public boolean existsByContentHash(String contentHash) {
        return jpaRepository.existsByContentHash(contentHash);
    }

    @Override
    public KnowledgeRecord save(KnowledgeRecord record) {
        return jpaRepository.save(record);
    }

    /** 기간 정규화 토큰(예: 2026-05-01~2026-05-31 또는 2026-05-01) 여부 */
    private boolean isPeriodToken(String token) {
        return token.matches("\\d{4}-\\d{2}-\\d{2}~\\d{4}-\\d{2}-\\d{2}")
                || token.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    /**
     * 랭킹 점수: 완전일치(3) &gt; 코드값일치(2) &gt; 부분일치(1).
     * 점수가 클수록 먼저 오도록 desc 정렬한다.
     */
    private OrderSpecifier<Integer> rankOrder(String keyword, Map<String, String> codeValues) {
        CaseBuilder caseBuilder = new CaseBuilder();
        boolean hasKeyword = StringUtils.hasText(keyword);
        boolean hasCode = codeValues != null && codeValues.values().stream().anyMatch(StringUtils::hasText);

        NumberExpression<Integer> score;
        if (hasKeyword) {
            // 제목 완전일치 > 코드값일치 > 부분일치
            NumberExpression<Integer> base = caseBuilder
                    .when(RECORD.title.equalsIgnoreCase(keyword)).then(3)
                    .when(RECORD.title.containsIgnoreCase(keyword)
                            .or(RECORD.body.contains(keyword))).then(1)  // body 는 CLOB → lower() 불가
                    .otherwise(0);
            score = hasCode ? base.add(1) : base; // 코드값 조건이 함께 걸리면 가중
        } else if (hasCode) {
            score = caseBuilder.when(RECORD.id.isNotNull()).then(2).otherwise(0);
        } else {
            score = caseBuilder.when(RECORD.id.isNotNull()).then(0).otherwise(0);
        }
        return score.desc();
    }
}
