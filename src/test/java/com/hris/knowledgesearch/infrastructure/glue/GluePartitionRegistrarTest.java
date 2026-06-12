package com.hris.knowledgesearch.infrastructure.glue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.glue.model.AlreadyExistsException;
import software.amazon.awssdk.services.glue.model.CreatePartitionRequest;
import software.amazon.awssdk.services.glue.model.GetTableRequest;
import software.amazon.awssdk.services.glue.model.GetTableResponse;
import software.amazon.awssdk.services.glue.model.StorageDescriptor;
import software.amazon.awssdk.services.glue.model.Table;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Glue 파티션 등록기 단위 테스트 (GlueClient 모킹 — AWS 불필요).
 */
@ExtendWith(MockitoExtension.class)
class GluePartitionRegistrarTest {

    private static final GlueProperties PROPERTIES = new GlueProperties(
            "ap-northeast-2",
            new GlueProperties.Glue(true, "knowledge_lake", "knowledge_archive"),
            new GlueProperties.S3("data-lake-bucket", "knowledge/"));

    @Mock
    private GlueClient glueClient;

    @Test
    @DisplayName("외부 테이블의 StorageDescriptor 를 복사하고 Location 만 파티션 경로로 바꿔 등록한다")
    void registersPartitionWithTableStorageDescriptor() {
        when(glueClient.getTable(any(GetTableRequest.class))).thenReturn(tableWithParquetDescriptor());
        var registrar = new GluePartitionRegistrar(glueClient, PROPERTIES);

        registrar.registerPartition(LocalDate.of(2026, 6, 12));

        ArgumentCaptor<CreatePartitionRequest> request = ArgumentCaptor.forClass(CreatePartitionRequest.class);
        verify(glueClient).createPartition(request.capture());

        assertThat(request.getValue().databaseName()).isEqualTo("knowledge_lake");
        assertThat(request.getValue().tableName()).isEqualTo("knowledge_archive");
        assertThat(request.getValue().partitionInput().values()).containsExactly("2026-06-12");
        assertThat(request.getValue().partitionInput().storageDescriptor().location())
                .isEqualTo("s3://data-lake-bucket/knowledge/dt=2026-06-12/");
        // 포맷 정의(Parquet SerDe 등)는 테이블 정의를 그대로 물려받는다
        assertThat(request.getValue().partitionInput().storageDescriptor().inputFormat())
                .isEqualTo("org.apache.hadoop.hive.ql.io.parquet.MapredParquetInputFormat");
    }

    @Test
    @DisplayName("이미 등록된 파티션(AlreadyExistsException)은 멱등으로 넘어간다")
    void alreadyExistingPartitionIsIdempotent() {
        when(glueClient.getTable(any(GetTableRequest.class))).thenReturn(tableWithParquetDescriptor());
        when(glueClient.createPartition(any(CreatePartitionRequest.class)))
                .thenThrow(AlreadyExistsException.builder().message("Partition already exists").build());
        var registrar = new GluePartitionRegistrar(glueClient, PROPERTIES);

        assertThatCode(() -> registrar.registerPartition(LocalDate.of(2026, 6, 12)))
                .doesNotThrowAnyException();
    }

    private GetTableResponse tableWithParquetDescriptor() {
        return GetTableResponse.builder()
                .table(Table.builder()
                        .name("knowledge_archive")
                        .storageDescriptor(StorageDescriptor.builder()
                                .location("s3://data-lake-bucket/knowledge/")
                                .inputFormat("org.apache.hadoop.hive.ql.io.parquet.MapredParquetInputFormat")
                                .outputFormat("org.apache.hadoop.hive.ql.io.parquet.MapredParquetOutputFormat")
                                .build())
                        .build())
                .build();
    }
}
