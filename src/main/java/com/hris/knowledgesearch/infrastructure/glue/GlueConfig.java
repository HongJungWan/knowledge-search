package com.hris.knowledgesearch.infrastructure.glue;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.glue.GlueClient;

/**
 * Glue 파티션 등록 빈 구성 (redshift 프로파일 + {@code aws.glue.enabled=true} 전용).
 * <p>
 * 자격증명은 AWS SDK 기본 체인(환경변수/EC2 인스턴스 프로파일)을 따른다 — 저장소에 두지 않는다.
 * local 프로파일에서는 이 구성 전체가 비활성이라 AWS 없이 부팅한다.
 */
@Configuration
@Profile("redshift")
@ConditionalOnProperty(prefix = "aws.glue", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(GlueProperties.class)
public class GlueConfig {

    @Bean
    public GlueClient glueClient(GlueProperties properties) {
        return GlueClient.builder()
                .region(Region.of(properties.region()))
                .build();
    }

    @Bean
    public GluePartitionRegistrar gluePartitionRegistrar(GlueClient glueClient, GlueProperties properties) {
        return new GluePartitionRegistrar(glueClient, properties);
    }

    @Bean
    public GluePartitionRegistrationListener gluePartitionRegistrationListener(GluePartitionRegistrar registrar) {
        return new GluePartitionRegistrationListener(registrar);
    }
}
