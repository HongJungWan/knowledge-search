package com.hris.knowledgesearch.infrastructure.embedding;

import com.hris.knowledgesearch.domain.knowledge.EmbeddingProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 결정론적 해싱 임베딩(기본 어댑터) — {@code localmodel}/{@code bedrock} 프로파일이 아닐 때 활성.
 * <p>
 * <b>의미 없는 스텁이다.</b> 문자 trigram 을 차원 버킷에 부호 해싱해 고정 길이 벡터를 만든다.
 * 목적은 배선·Flyway 마이그레이션·RRF SQL·백필 경로를 AWS/모델 없이 결정론적으로 검증하는 것뿐이다.
 * 실제 의미적 재현율(실효성)은 측정하지 못한다 — 그건 {@code LocalModelEmbeddingProvider}(bge-m3) 또는
 * {@code BedrockEmbeddingProvider}(Titan v2) 의 몫이다. CI 는 이 스텁의 재현율 수치로 게이팅하지 않는다.
 */
@Component
@Profile("!localmodel & !bedrock")
public class HashingEmbeddingProvider implements EmbeddingProvider {

    private final int dimension;

    public HashingEmbeddingProvider(@Value("${embedding.dimension:1024}") int dimension) {
        this.dimension = dimension;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimension];
        if (text == null || text.isBlank()) {
            return vector; // 영벡터(코사인 거리 정의상 결과에 영향 없음)
        }
        String normalized = text.toLowerCase();
        // 문자 trigram 부호 해싱 — 짧은 문자열은 전체를 1개 토큰으로 처리한다.
        if (normalized.length() < 3) {
            accumulate(vector, normalized);
        } else {
            for (int i = 0; i + 3 <= normalized.length(); i++) {
                accumulate(vector, normalized.substring(i, i + 3));
            }
        }
        return l2Normalize(vector);
    }

    private void accumulate(float[] vector, String token) {
        int hash = token.hashCode();
        int index = Math.floorMod(hash, dimension);
        float sign = (hash & 1) == 0 ? 1f : -1f;
        vector[index] += sign;
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
}
