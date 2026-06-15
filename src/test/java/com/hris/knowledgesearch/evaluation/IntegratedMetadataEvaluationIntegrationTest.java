package com.hris.knowledgesearch.evaluation;

import com.hris.knowledgesearch.application.evaluation.IntegratedEvaluationService;
import com.hris.knowledgesearch.application.evaluation.dto.response.IntegratedEvaluationReportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KS→MO 2-홉 통합 실효성 측정(옵트인) — 도커 pgvector + Ollama(bge-m3) + 라이브 MO(8096) 필요.
 * <p>
 * 조인트 회귀 가드: 50/50 다중 코드차원 정답셋({@code gold_search_eval.csv}) + 100-doc 코퍼스
 * ({@code settlement-source-eval.json})로 "온톨로지 참조가 벡터 위에 추가 값을 더하는가"(H3)를 자동 단언한다.
 * <p>
 * 사전 준비:
 * <ol>
 *   <li>{@code docker compose -f docker/docker-compose.postgres.yml up -d}</li>
 *   <li>{@code ollama pull bge-m3 && ollama serve}</li>
 *   <li>metadata-ontology 기동: {@code (cd ../metadata-ontology && ./gradlew bootRun)} (8096)</li>
 *   <li>{@code RUN_SEMANTIC_EVAL=true ./gradlew test --tests '*IntegratedMetadataEvaluationIntegrationTest'}</li>
 * </ol>
 * {@code RUN_SEMANTIC_EVAL=true} 가 없으면 비활성(결정론 스텁 수치로 게이팅하지 않는다는 원칙).
 * {@code metadata.enabled=true} + eval 코퍼스 적재는 아래 프로퍼티로 강제한다.
 */
@SpringBootTest(properties = {
        "metadata.enabled=true",
        "etl.source-resource=sample/settlement-source-eval.json"
})
@ActiveProfiles({"postgres", "localmodel"})
@Tag("semantic")
@EnabledIfEnvironmentVariable(named = "RUN_SEMANTIC_EVAL", matches = "true")
class IntegratedMetadataEvaluationIntegrationTest {

    private static final String GOLD = "evaluation/gold_search_eval.csv";

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job settlementIngestionJob;

    @Autowired
    private IntegratedEvaluationService integratedEvaluationService;

    @BeforeEach
    void loadData() throws Exception {
        // eval 코퍼스 적재(멱등 — content_hash 중복 skip). Postgres 어댑터 save() 가 임베딩을 함께 기록한다.
        jobLauncher.run(settlementIngestionJob, new JobParametersBuilder()
                .addLong("requestedAt", System.currentTimeMillis())
                .toJobParameters());
    }

    @Test
    @DisplayName("MO 참조가 벡터 위에 추가 값을 더한다(H3) — 정형 무회귀(H4), 커버리지·비포화 전제 충족")
    void metadataAddsValueOverVectorWithoutStructuredRegression() {
        IntegratedEvaluationReportResponse report = integratedEvaluationService.evaluate(3, 10, GOLD);

        System.out.println("[INTEGRATED EVAL] " + report.interpretation().summary());
        System.out.println("  coverage=" + report.metadataCodeValueCoverage()
                + " VECTOR.overall.p@3=" + report.vectorOnly().overall().precisionAtK()
                + " INTEGRATED.overall.p@3=" + report.integrated().overall().precisionAtK());

        // 전제 1: MO 코드값 커버리지 ≥ 0.50 (미만이면 H2/H3 vacuous → "어휘 수정" 판정).
        assertThat(report.metadataCodeValueCoverage())
                .as("MO 코드값 커버리지(정형 해석 가능 비율)")
                .isGreaterThanOrEqualTo(0.50);

        // 전제 2: 코퍼스 비포화 — 벡터가 비정형 recall@3 을 1.0 으로 포화시키지 않아야 H3 를 관측 가능.
        assertThat(report.vectorOnly().unstructured().recallAtK())
                .as("비정형 벡터 recall@3 비포화(<1.0)")
                .isLessThan(1.0);

        // H1: 벡터가 비정형에 기여.
        assertThat(report.interpretation().vectorHelpsUnstructured())
                .as("H1 — VECTOR > KEYWORD (비정형)")
                .isTrue();

        // H2: MO 코드필터가 정형에 기여.
        assertThat(report.interpretation().metadataHelpsStructured())
                .as("H2 — META > KEYWORD (정형)")
                .isTrue();

        // H3(핵심): MO 가 벡터 위에 추가 값을 더한다 (metadata OFF→ON 델타).
        assertThat(report.interpretation().metadataAddsOverVector())
                .as("H3 — INTEGRATED > VECTOR (전체). KS→MO 2-홉 구조의 실효성 핵심 증거")
                .isTrue();

        // H4: 하이브리드 전환의 정형 무회귀.
        assertThat(report.integrated().structured().recallAtK())
                .as("H4 — INTEGRATED 정형 recall@3 무회귀")
                .isGreaterThanOrEqualTo(report.vectorOnly().structured().recallAtK() - 1e-9);
    }
}
