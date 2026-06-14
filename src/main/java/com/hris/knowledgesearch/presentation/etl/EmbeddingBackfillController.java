package com.hris.knowledgesearch.presentation.etl;

import com.hris.knowledgesearch.application.knowledge.port.EmbeddingBackfillPort;
import com.hris.knowledgesearch.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 임베딩 백필 수동 트리거 API — postgres 프로파일 전용(pgvector 도입 후 기존 데이터 임베딩 채움).
 * <p>
 * 애플리케이션 포트({@link EmbeddingBackfillPort})에 위임한다 — 구현은 infrastructure(postgres 어댑터).
 */
@Tag(name = "ETL", description = "임베딩 백필 수동 트리거 (postgres)")
@RestController
@RequestMapping("/etl/embeddings")
@Profile("postgres")
@RequiredArgsConstructor
public class EmbeddingBackfillController {

    private final EmbeddingBackfillPort embeddingBackfillPort;

    @Operation(summary = "임베딩 백필", description = "embedding 이 비어 있는 지식 레코드에 임베딩을 채운다(멱등).")
    @PostMapping("/backfill")
    public ResponseEntity<ApiResponse<Map<String, Object>>> backfill() {
        int updated = embeddingBackfillPort.backfill();
        return ResponseEntity.ok(ApiResponse.success(Map.of("updated", updated)));
    }
}
