package com.hris.knowledgesearch.infrastructure.embedding;

import com.hris.knowledgesearch.domain.knowledge.EmbeddingProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 로컬 오픈소스 임베딩(실효성 측정용) — {@code localmodel} 프로파일에서 활성.
 * <p>
 * Ollama 호환 HTTP 엔드포인트({@code POST /api/embeddings}, 기본 bge-m3, 1024차원·다국어/한국어)를 호출한다.
 * 무거운 JVM ML 의존성(DJL/ONNX, ~2GB 모델 아티팩트) 없이 로컬 모델을 쓰기 위한 선택이다 — 사전 준비:
 * {@code ollama pull bge-m3 && ollama serve}. AWS 불필요.
 * <p>
 * 실측은 옵트인(@Tag("semantic")) 테스트에서만 — Ollama 가 떠 있어야 하므로 CI 기본 게이트에는 넣지 않는다.
 * 코사인 안전을 위해 응답 벡터를 L2 정규화한다(bge-m3 는 보통 이미 정규화되어 있다).
 */
@Component
@Profile("localmodel")
public class LocalModelEmbeddingProvider implements EmbeddingProvider {

    private final RestClient client;
    private final String model;
    private final int dimension;

    public LocalModelEmbeddingProvider(
            @Value("${embedding.local.base-url:http://localhost:11434}") String baseUrl,
            @Value("${embedding.local.model:bge-m3}") String model,
            @Value("${embedding.dimension:1024}") int dimension) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
        this.model = model;
        this.dimension = dimension;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public float[] embed(String text) {
        OllamaEmbeddingResponse response = client.post()
                .uri("/api/embeddings")
                .body(Map.of("model", model, "prompt", text == null ? "" : text))
                .retrieve()
                .body(OllamaEmbeddingResponse.class);

        if (response == null || response.embedding() == null) {
            throw new IllegalStateException("임베딩 응답이 비어 있습니다 (model=" + model + ")");
        }
        double[] raw = response.embedding();
        if (raw.length != dimension) {
            throw new IllegalStateException(
                    "임베딩 차원 불일치: 응답=" + raw.length + ", 설정=" + dimension
                            + " (embedding.dimension 과 모델/마이그레이션 vector(N) 을 맞추세요)");
        }
        float[] vector = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            vector[i] = (float) raw[i];
        }
        return l2Normalize(vector);
    }

    private float[] l2Normalize(float[] vector) {
        double norm = 0;
        for (float value : vector) {
            norm += (double) value * value;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) (vector[i] / norm);
            }
        }
        return vector;
    }

    /** Ollama {@code /api/embeddings} 응답. */
    record OllamaEmbeddingResponse(double[] embedding) {
    }
}
