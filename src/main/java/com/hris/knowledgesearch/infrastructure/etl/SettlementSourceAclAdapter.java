package com.hris.knowledgesearch.infrastructure.etl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.knowledgesearch.application.knowledge.command.IngestKnowledgeCommand;
import com.hris.knowledgesearch.application.knowledge.port.SettlementSourceAcl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * 정산 외부 소스 ACL 구현 (아웃바운드 어댑터).
 * <p>
 * 외부 표현 → 도메인 적재 커맨드 번역. 정규화({@link TextNormalizer})·해시({@link HashUtil})·기본값·검증을
 * 한곳에 모은다. 기존 ETL 프로세서가 인라인으로 하던 변환과 출력이 동일하다(동작 보존).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementSourceAclAdapter implements SettlementSourceAcl {

    private final ObjectMapper objectMapper;

    @Override
    public Optional<IngestKnowledgeCommand> toIngestCommand(
            String domain, String title, String body, String sourceUrl, Map<String, String> codeValues) {
        // (1) 필수 필드 검증 — 누락 시 skip
        if (!StringUtils.hasText(title) || !StringUtils.hasText(body)) {
            return Optional.empty();
        }

        // (2) 문자열 정규화
        String normalizedDomain = StringUtils.hasText(domain)
                ? TextNormalizer.normalize(domain).toUpperCase()
                : "SETTLEMENT";
        String normalizedTitle = TextNormalizer.normalize(title);
        String normalizedBody = TextNormalizer.normalize(body);
        String normalizedSourceUrl = sourceUrl == null ? null : TextNormalizer.normalize(sourceUrl);

        String codeValuesJson = null;
        if (codeValues != null && !codeValues.isEmpty()) {
            try {
                codeValuesJson = objectMapper.writeValueAsString(codeValues);
            } catch (Exception e) {
                log.warn("[ETL] codeValues 직렬화 실패: {}", e.getMessage());
            }
        }

        // (3) SHA-256 content_hash
        String contentHash = HashUtil.contentHash(normalizedDomain, normalizedTitle, normalizedBody);

        return Optional.of(new IngestKnowledgeCommand(
                normalizedDomain, normalizedTitle, normalizedBody, normalizedSourceUrl,
                codeValuesJson, Instant.now(), contentHash));
    }
}
