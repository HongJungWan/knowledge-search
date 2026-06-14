package com.hris.knowledgesearch.presentation.evaluation;

import com.hris.knowledgesearch.application.evaluation.HybridEvaluationService;
import com.hris.knowledgesearch.application.evaluation.IntegratedEvaluationService;
import com.hris.knowledgesearch.application.evaluation.dto.response.HybridEvaluationReportResponse;
import com.hris.knowledgesearch.application.evaluation.dto.response.IntegratedEvaluationReportResponse;
import com.hris.knowledgesearch.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 하이브리드 검색 실효성 평가 API — postgres 프로파일 전용.
 * <p>
 * KEYWORD vs HYBRID 를 50/50 정답셋으로 비교한 리포트를 반환한다(채택 게이트 포함).
 * 의미 있는 수치는 +localmodel(Ollama bge-m3) 또는 +bedrock 에서 나온다.
 */
@Tag(name = "Evaluation", description = "하이브리드 검색 실효성 평가 (postgres)")
@RestController
@RequestMapping("/api/admin/evaluation")
@Profile("postgres")
@RequiredArgsConstructor
public class EvaluationController {

    private final HybridEvaluationService hybridEvaluationService;
    private final IntegratedEvaluationService integratedEvaluationService;

    @Operation(summary = "하이브리드 평가", description = "KEYWORD vs HYBRID recall@k/MRR/nDCG 비교 + 채택 게이트.")
    @GetMapping("/hybrid")
    public ResponseEntity<ApiResponse<HybridEvaluationReportResponse>> hybrid(
            @RequestParam(defaultValue = "3") int k,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.success(hybridEvaluationService.evaluate(k, limit)));
    }

    @Operation(summary = "KS→MO 구조 ablation",
            description = "metadata ON/OFF × vector ON/OFF 4-arm 으로 온톨로지 참조 구조의 실효성을 측정한다. "
                    + "gold=large 면 정밀도 압력 대량 코퍼스 정답셋 사용.")
    @GetMapping("/integrated")
    public ResponseEntity<ApiResponse<IntegratedEvaluationReportResponse>> integrated(
            @RequestParam(defaultValue = "3") int k,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "default") String gold) {
        String resource = "large".equalsIgnoreCase(gold)
                ? "evaluation/gold_search_large.csv"
                : "evaluation/gold_search.csv";
        return ResponseEntity.ok(ApiResponse.success(integratedEvaluationService.evaluate(k, limit, resource)));
    }
}
