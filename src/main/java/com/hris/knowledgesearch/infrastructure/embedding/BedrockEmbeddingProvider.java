package com.hris.knowledgesearch.infrastructure.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hris.knowledgesearch.domain.knowledge.EmbeddingProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

/**
 * AWS Bedrock 임베딩(향후 운영) — {@code bedrock} 프로파일에서 활성.
 * <p>
 * Amazon Titan Text Embeddings V2({@code amazon.titan-embed-text-v2:0}) 호출. 100+ 언어(한국어 포함),
 * 출력 차원 256/512/1024 설정 가능(여기선 {@code embedding.dimension}), 단위 정규화 → 코사인.
 * 자격증명은 기본 제공자 체인(env/instance profile)으로 해석한다 — 빈 생성에는 AWS 계정이 필요 없다.
 * 향후 운영 경로는 OpenSearch(k-NN) + 이 provider 조합이며, 본 계획의 SQL RRF 가 OpenSearch 네이티브 RRF 로 이식된다.
 */
@Component
@Profile("bedrock")
public class BedrockEmbeddingProvider implements EmbeddingProvider {

    private final BedrockRuntimeClient client;
    private final ObjectMapper objectMapper;
    private final String modelId;
    private final int dimension;

    public BedrockEmbeddingProvider(
            ObjectMapper objectMapper,
            @Value("${aws.region:ap-northeast-2}") String region,
            @Value("${embedding.bedrock.model-id:amazon.titan-embed-text-v2:0}") String modelId,
            @Value("${embedding.dimension:1024}") int dimension) {
        this.objectMapper = objectMapper;
        this.modelId = modelId;
        this.dimension = dimension;
        this.client = BedrockRuntimeClient.builder().region(Region.of(region)).build();
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public float[] embed(String text) {
        try {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("inputText", text == null ? "" : text);
            request.put("dimensions", dimension);
            request.put("normalize", true);

            InvokeModelResponse response = client.invokeModel(InvokeModelRequest.builder()
                    .modelId(modelId)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(objectMapper.writeValueAsString(request)))
                    .build());

            JsonNode body = objectMapper.readTree(response.body().asUtf8String());
            JsonNode embedding = body.get("embedding");
            if (embedding == null || !embedding.isArray() || embedding.size() != dimension) {
                throw new IllegalStateException("Bedrock 임베딩 차원 불일치/누락: " + modelId);
            }
            float[] vector = new float[dimension];
            for (int i = 0; i < dimension; i++) {
                vector[i] = (float) embedding.get(i).asDouble();
            }
            return vector; // Titan v2 normalize=true → 이미 단위벡터
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Bedrock 임베딩 호출 실패: " + e.getMessage(), e);
        }
    }
}
