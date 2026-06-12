package com.hris.knowledgesearch.infrastructure.glue;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Glue Data Catalog 파티션 등록 설정 (application-redshift.yml 의 {@code aws.*}).
 *
 * @param region AWS 리전
 * @param glue   Glue Data Catalog 데이터베이스/외부 테이블 (scripts/redshift/02)
 * @param s3     Parquet 데이터 레이크 버킷/프리픽스 — 파티션 Location 의 루트
 */
@ConfigurationProperties(prefix = "aws")
public record GlueProperties(String region, Glue glue, S3 s3) {

    public record Glue(boolean enabled, String database, String table) {
    }

    public record S3(String bucket, String prefix) {
    }
}
