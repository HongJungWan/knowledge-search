package com.hris.knowledgesearch.presentation.etl;

import com.hris.knowledgesearch.global.common.ApiResponse;
import com.hris.knowledgesearch.global.exception.BusinessException;
import com.hris.knowledgesearch.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * ETL 수동 트리거 API (PRD §7.2 — batch-2.0 에 없던 수동 실행 보완).
 * <p>
 * 운영에서는 스케줄(매시 정각)도 함께 두지만, 여기서는 수동 실행 엔드포인트를 제공한다.
 */
@Slf4j
@Tag(name = "ETL", description = "정산 지식 적재 잡 수동 트리거")
@RestController
@RequestMapping("/etl")
@RequiredArgsConstructor
public class EtlController {

    private final JobLauncher jobLauncher;
    private final Job settlementIngestionJob;

    @Operation(summary = "적재 잡 실행", description = "정산 지식 ETL 적재 잡을 수동으로 실행한다.")
    @PostMapping("/run")
    public ResponseEntity<ApiResponse<Map<String, Object>>> run() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("requestedAt", System.currentTimeMillis())
                    .toJobParameters();
            var execution = jobLauncher.run(settlementIngestionJob, params);
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                    "jobName", execution.getJobInstance().getJobName(),
                    "status", execution.getStatus().name(),
                    "exitCode", execution.getExitStatus().getExitCode())));
        } catch (Exception e) {
            log.error("[ETL] 잡 실행 실패", e);
            throw new BusinessException(ErrorCode.ETL_TRIGGER_FAILED, e.getMessage());
        }
    }
}
