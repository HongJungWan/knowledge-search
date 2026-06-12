package com.hris.knowledgesearch;

import com.hris.knowledgesearch.domain.knowledge.KnowledgeRecordRepository;
import com.hris.knowledgesearch.domain.knowledge.SearchLogRepository;
import com.hris.knowledgesearch.infrastructure.glue.GluePartitionRegistrationListener;
import com.hris.knowledgesearch.infrastructure.persistence.knowledge.RedshiftKnowledgeRecordRepositoryImpl;
import com.hris.knowledgesearch.infrastructure.persistence.knowledge.RedshiftSearchLogRepositoryImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * redshift 프로파일 빈 와이어링 기동 스모크 (P0 검증 — AWS 불필요).
 * <p>
 * 모킹 단위 테스트는 SQL 조립만 검증할 뿐 <b>프로파일 배타성·ObjectProvider 주입·
 * 트랜잭션 매니저 분리가 실제로 조립되는지</b>는 컨텍스트를 띄워야만 알 수 있다.
 * Redshift datasource 는 H2(PostgreSQL 모드)로 가장하고, Glue 설정은 더미 값을 준다 —
 * GlueClient 는 자격증명을 요청 시점에 해석하므로 빈 생성까지는 AWS 계정이 필요 없다.
 * (실제 Redshift 가 SQL 을 수용하는지는 이 테스트의 범위 밖 — scripts/redshift/README 참조)
 */
@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:redshift-wiring;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "aws.region=ap-northeast-2",
        "aws.glue.enabled=true",
        "aws.glue.database=wiring-test",
        "aws.glue.table=knowledge_archive",
        "aws.s3.bucket=wiring-test-bucket",
        "aws.s3.prefix=knowledge/"
})
@ActiveProfiles("redshift")
class RedshiftProfileWiringTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("포트 어댑터는 Redshift 구현체가 단일 선택된다 (JPA 어댑터는 비활성)")
    void portAdaptersAreRedshiftImplementations() {
        assertThat(context.getBean(KnowledgeRecordRepository.class))
                .isInstanceOf(RedshiftKnowledgeRecordRepositoryImpl.class);
        assertThat(context.getBean(SearchLogRepository.class))
                .isInstanceOf(RedshiftSearchLogRepositoryImpl.class);
    }

    @Test
    @DisplayName("Glue 파티션 등록 리스너가 잡에 주입 가능하게 빈으로 존재한다")
    void gluePartitionListenerIsWired() {
        assertThat(context.getBean(GluePartitionRegistrationListener.class)).isNotNull();
        assertThat(context.containsBean("settlementIngestionJob")).isTrue();
    }

    @Test
    @DisplayName("배치 메타테이블 DataSource·트랜잭션 매니저가 주 DataSource 와 분리된다")
    void batchInfrastructureIsSeparatedFromPrimaryDataSource() {
        DataSource primary = context.getBean("dataSource", DataSource.class);
        DataSource batch = context.getBean("batchDataSource", DataSource.class);
        assertThat(batch).isNotSameAs(primary);

        PlatformTransactionManager batchTx =
                context.getBean("batchTransactionManager", PlatformTransactionManager.class);
        PlatformTransactionManager primaryTx =
                context.getBean("transactionManager", PlatformTransactionManager.class);
        assertThat(batchTx).isNotSameAs(primaryTx);
    }
}
