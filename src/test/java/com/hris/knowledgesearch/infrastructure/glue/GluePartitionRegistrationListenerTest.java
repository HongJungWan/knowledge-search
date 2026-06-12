package com.hris.knowledgesearch.infrastructure.glue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Glue 파티션 등록 배치 리스너 단위 테스트.
 * <p>
 * COMPLETED 잡만 파티션을 등록하고(실패 잡의 불완전 데이터 노출 방지),
 * 파티션 일자는 잡 시작 일자를 쓴다.
 */
@ExtendWith(MockitoExtension.class)
class GluePartitionRegistrationListenerTest {

    @Mock
    private GluePartitionRegistrar registrar;

    @Test
    @DisplayName("COMPLETED 잡: 잡 시작 일자의 파티션을 등록한다")
    void registersPartitionForCompletedJob() {
        var listener = new GluePartitionRegistrationListener(registrar);
        JobExecution jobExecution = new JobExecution(1L);
        jobExecution.setStatus(BatchStatus.COMPLETED);
        jobExecution.setStartTime(LocalDateTime.of(2026, 6, 12, 3, 0));

        listener.afterJob(jobExecution);

        verify(registrar).registerPartition(LocalDate.of(2026, 6, 12));
    }

    @Test
    @DisplayName("FAILED 잡: 파티션을 등록하지 않는다")
    void skipsPartitionForFailedJob() {
        var listener = new GluePartitionRegistrationListener(registrar);
        JobExecution jobExecution = new JobExecution(1L);
        jobExecution.setStatus(BatchStatus.FAILED);

        listener.afterJob(jobExecution);

        verifyNoInteractions(registrar);
    }
}
