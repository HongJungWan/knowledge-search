package com.hris.knowledgesearch.infrastructure.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.batch.BatchDataSource;
import org.springframework.boot.autoconfigure.batch.BatchTransactionManager;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * redshift 프로파일의 DataSource 구성 — 주(Redshift)·배치 메타(H2) 분리.
 * <p>
 * Spring Batch 의 JobRepository 메타테이블은 시퀀스(BATCH_JOB_SEQ 등)와 행 단위
 * 잠금을 요구하는데 Redshift 는 시퀀스를 지원하지 않는다. 따라서 운영에서도 배치
 * 메타데이터는 임베디드 H2 에 두고({@code @BatchDataSource}), 업무 데이터(검색·적재)만
 * Redshift 주 DataSource 를 쓴다. local 프로파일은 주 DataSource 가 H2 라 분리가 필요 없다.
 * <ul>
 *   <li><b>주 DataSource 를 여기서 명시 정의하는 이유</b>: 사용자 정의 DataSource 빈
 *       (batchDataSource)이 하나라도 있으면 Boot 의 DataSource 자동구성이
 *       {@code @ConditionalOnMissingBean(DataSource)} 로 전부 물러나, JPA·JdbcTemplate 이
 *       배치용 H2 를 잡아버린다(와이어링 스모크 테스트로 실증). {@code @Primary} +
 *       {@code spring.datasource.*} 바인딩으로 자동구성과 동일하게 복원한다.</li>
 *   <li><b>트랜잭션 매니저 분리</b>({@code @BatchTransactionManager}): 없으면 JobRepository 가
 *       주 DataSource(Redshift)에 묶인 기본 트랜잭션 매니저를 써서 H2 메타테이블 쓰기가
 *       트랜잭션 동기화 밖으로 빠진다. 청크 스텝의 업무 트랜잭션은 기존대로 주 트랜잭션
 *       매니저({@code IngestionJobConfig} 의 파라미터명 {@code transactionManager} 빈)를 쓴다.</li>
 *   <li><b>주 트랜잭션 매니저도 명시 정의</b>: 사용자 정의 TransactionManager 빈
 *       (batchTransactionManager)이 있으면 JPA 의 {@code transactionManager} 자동구성 역시
 *       {@code @ConditionalOnMissingBean(TransactionManager)} 로 물러난다 — 같은 backoff 함정의
 *       두 번째 사례(와이어링 스모크 테스트로 실증). 자동구성과 동일한 JpaTransactionManager 로 복원한다.</li>
 * </ul>
 */
@Configuration
@Profile("redshift")
public class BatchDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    /** 주(Redshift) DataSource — application-redshift.yml 의 spring.datasource.* / hikari.* 바인딩. */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties dataSourceProperties) {
        return dataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    /** 주(업무) 트랜잭션 매니저 — JPA 자동구성이 물러난 자리를 동일 구성으로 복원. */
    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    @BatchDataSource
    public DataSource batchDataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("/org/springframework/batch/core/schema-h2.sql")
                .generateUniqueName(true)
                .build();
    }

    @Bean
    @BatchTransactionManager
    public PlatformTransactionManager batchTransactionManager(@BatchDataSource DataSource batchDataSource) {
        return new JdbcTransactionManager(batchDataSource);
    }
}
