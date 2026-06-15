package com.hris.knowledgesearch.infrastructure.metadata;

import com.hris.knowledgesearch.application.knowledge.port.SchemaCatalogResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R2 ACL 폴백 가드: metadata 비활성/도달 불가 시 {@code unavailable()} 폴백을 반환한다.
 * <p>
 * 소비자(서비스)는 이때 정적 카탈로그로 폴백하므로 list_schema 는 metadata 없이도 동작한다.
 */
class MetadataSchemaClientTest {

    @Test
    void returnsUnavailableWhenDisabled() {
        MetadataSchemaClient client = new MetadataSchemaClient(false, "http://localhost:8096");

        SchemaCatalogResult result = client.listSchema("SETTLEMENT");

        assertThat(result.available()).isFalse();
        assertThat(result.columns()).isEmpty();
    }

    @Test
    void returnsUnavailableWhenMetadataUnreachable() {
        // 활성화됐지만 도달 불가능한 포트 → 예외를 폴백으로 흡수
        MetadataSchemaClient client = new MetadataSchemaClient(true, "http://localhost:1");

        SchemaCatalogResult result = client.listSchema("SETTLEMENT");

        assertThat(result.available()).isFalse();
        assertThat(result.columns()).isEmpty();
    }
}
