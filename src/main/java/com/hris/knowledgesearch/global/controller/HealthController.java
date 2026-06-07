package com.hris.knowledgesearch.global.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * 헬스 체크 컨트롤러.
 */
@Tag(name = "Health", description = "시스템 헬스 체크 API")
@RestController
public class HealthController {

    @Operation(summary = "시스템 헬스 체크", description = "서비스 가용 상태를 반환한다.")
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "application", "knowledge-search",
                "timestamp", Instant.now()
        ));
    }
}
