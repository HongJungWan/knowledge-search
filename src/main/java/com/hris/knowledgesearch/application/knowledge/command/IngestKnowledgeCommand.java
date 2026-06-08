package com.hris.knowledgesearch.application.knowledge.command;

import java.time.Instant;

/**
 * 지식 적재 커맨드 (ETL 경로). 외부 소스를 anti-corruption 으로 번역한 결과로,
 * 도메인 팩토리 {@code KnowledgeRecord.forIngestion(...)} 에 그대로 넘길 수 있는 정규화·해시 완료 상태다.
 * <p>
 * 외부 소스 표현(JSON, 공백/대소문자/중복 포함)은 이 커맨드 경계 바깥에 둔다(ACL).
 */
public record IngestKnowledgeCommand(
        String domain,
        String title,
        String body,
        String sourceUrl,
        String codeValues,
        Instant sourceUpdatedAt,
        String contentHash
) {
}
