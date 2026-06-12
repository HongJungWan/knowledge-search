package com.hris.knowledgesearch;

import com.hris.knowledgesearch.application.knowledge.KnowledgeSearchService;
import com.hris.knowledgesearch.application.knowledge.dto.KnowledgeDetailResponse;
import com.hris.knowledgesearch.application.knowledge.dto.KnowledgeSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QueryDSL 검색 어댑터 end-to-end 통합 테스트.
 * <p>
 * 기본(local) 프로필로 부팅해 {@code data.sql} 시드가 H2 에 올라간 상태에서, 실제 리포지토리 어댑터
 * + QueryDSL 경로를 통해 검색·단건조회가 동작하는지 확인한다. metadata 는 비활성(no-op 폴백) 상태다.
 */
@SpringBootTest
class KnowledgeSearchIntegrationTest {

    @Autowired
    private KnowledgeSearchService knowledgeSearchService;

    @Test
    @DisplayName("search('미정산') 은 시드된 PENDING 레코드 1건을 반환한다 (data.sql id 3)")
    void search_returnsSeededPendingRecord() {
        List<KnowledgeSummaryResponse> results = knowledgeSearchService.search("미정산", null, null, 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).contains("미정산");
    }

    @Test
    @DisplayName("LIKE 와일드카드: 키워드의 % 는 리터럴로 이스케이프된다 (Redshift 경로와 동일 시맨틱 — tripwire)")
    void search_escapesLikeWildcardsToLiteral() {
        // QueryDSL contains/containsIgnoreCase 는 상수의 %/_ 를 이스케이프한다(실측 —
        // '%' 검색은 본문에 리터럴 % 가 있는 '가맹점 수수료율(2.8%)' 1건만 매칭).
        // RedshiftKnowledgeRecordRepositoryImpl 도 ESCAPE '!' 로 동일 시맨틱을 구현한다 —
        // 이 테스트가 깨지면(QueryDSL 동작 변경) Redshift 경로의 이스케이프도 함께 맞춰야 한다.
        List<KnowledgeSummaryResponse> results = knowledgeSearchService.search("%", null, null, 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).contains("수수료율");
    }

    @Test
    @DisplayName("getRecord 는 검색된 레코드의 상세를 반환한다")
    void getRecord_returnsDetailForSearchedRecord() {
        List<KnowledgeSummaryResponse> results = knowledgeSearchService.search("미정산", null, null, 10);
        assertThat(results).hasSize(1);
        Long id = results.get(0).getId();

        KnowledgeDetailResponse detail = knowledgeSearchService.getRecord(id);

        assertThat(detail).isNotNull();
        assertThat(detail.getId()).isEqualTo(id);
        assertThat(detail.getTitle()).contains("미정산");
    }
}
