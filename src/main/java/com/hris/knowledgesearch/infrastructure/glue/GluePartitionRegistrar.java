package com.hris.knowledgesearch.infrastructure.glue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.glue.model.AlreadyExistsException;
import software.amazon.awssdk.services.glue.model.CreatePartitionRequest;
import software.amazon.awssdk.services.glue.model.GetTableRequest;
import software.amazon.awssdk.services.glue.model.PartitionInput;
import software.amazon.awssdk.services.glue.model.StorageDescriptor;

import java.time.LocalDate;

/**
 * Glue Data Catalog 파티션 등록기 (PRD §4.2/§7).
 * <p>
 * Spectrum 은 카탈로그에 등록된 파티션만 조회하므로, ETL 적재가 끝나면 해당 일자
 * 파티션({@code dt=YYYY-MM-DD})을 등록해 레이크 파이프라인이 쓴 당일 아카이브 파티션이
 * Spectrum 조회에 노출되게 한다(이 ETL 산출물은 네이티브 테이블 — 파티션 데이터 생산 주체와 구분).
 * <ul>
 *   <li>등록 방식: AWS SDK {@code glue:CreatePartition} — Redshift 의 파티션 DDL
 *       ({@code ALTER TABLE ... ADD PARTITION})은 트랜잭션 블록 안에서 실행할 수 없어
 *       배치 트랜잭션과 충돌 소지가 있고, 파티션 메타데이터의 책임 주체는 카탈로그(Glue)다.</li>
 *   <li>StorageDescriptor 는 외부 테이블 정의를 복사하고 Location 만 파티션 경로로 바꾼다 —
 *       포맷(Parquet/SerDe) 정의가 테이블과 항상 일치하도록.</li>
 *   <li>멱등성: 이미 등록된 파티션이면 {@link AlreadyExistsException} 을 받아 조용히 넘어간다.</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class GluePartitionRegistrar {

    private final GlueClient glueClient;
    private final GlueProperties properties;

    /** 해당 일자 파티션을 Glue Data Catalog 에 등록한다 (멱등). */
    public void registerPartition(LocalDate date) {
        String dt = date.toString();
        String database = properties.glue().database();
        String table = properties.glue().table();

        StorageDescriptor tableDescriptor = glueClient.getTable(GetTableRequest.builder()
                        .databaseName(database)
                        .name(table)
                        .build())
                .table()
                .storageDescriptor();

        String location = "s3://" + properties.s3().bucket() + "/" + properties.s3().prefix()
                + "dt=" + dt + "/";
        StorageDescriptor partitionDescriptor = tableDescriptor.toBuilder()
                .location(location)
                .build();

        try {
            glueClient.createPartition(CreatePartitionRequest.builder()
                    .databaseName(database)
                    .tableName(table)
                    .partitionInput(PartitionInput.builder()
                            .values(dt)
                            .storageDescriptor(partitionDescriptor)
                            .build())
                    .build());
            log.info("[Glue] 파티션 등록 완료: {}.{} dt={} location={}", database, table, dt, location);
        } catch (AlreadyExistsException e) {
            log.info("[Glue] 이미 등록된 파티션 — 멱등 skip: {}.{} dt={}", database, table, dt);
        }
    }
}
