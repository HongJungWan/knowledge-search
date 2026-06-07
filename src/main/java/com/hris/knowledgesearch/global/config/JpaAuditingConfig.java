package com.hris.knowledgesearch.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 설정.
 * <p>
 * BaseEntity 의 createdAt/updatedAt 를 자동으로 채운다.
 * 본 서비스는 인증 컨텍스트가 없으므로 작성자(AuditorAware) 는 두지 않는다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
