package com.hris.knowledgesearch.infrastructure.glue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ETL 잡 완료 시 Glue 파티션을 등록하는 배치 리스너 (PRD §4.2/§7).
 * <p>
 * {@code COMPLETED} 로 끝난 잡에 대해서만 잡 시작 일자의 파티션을 등록한다 —
 * 실패한 잡의 파티션을 등록하면 불완전한 데이터가 조회에 노출될 수 있다.
 * redshift 프로파일에서만 빈으로 등록되며(local 은 리스너 부재 → 동작 불변),
 * {@code IngestionJobConfig} 가 {@code ObjectProvider} 로 주입한다.
 */
@Slf4j
@RequiredArgsConstructor
public class GluePartitionRegistrationListener implements JobExecutionListener {

    private final GluePartitionRegistrar registrar;

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
            log.warn("[Glue] 잡 상태가 COMPLETED 가 아니라 파티션 등록을 생략한다: {}",
                    jobExecution.getStatus());
            return;
        }
        LocalDateTime startTime = jobExecution.getStartTime();
        LocalDate partitionDate = startTime != null ? startTime.toLocalDate() : LocalDate.now();
        registrar.registerPartition(partitionDate);
    }
}
