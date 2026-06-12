package com.hris.knowledgesearch.application.knowledge.port;

import com.hris.knowledgesearch.application.knowledge.command.IngestKnowledgeCommand;

import java.util.Map;
import java.util.Optional;

/**
 * 정산 외부 소스 ACL (anti-corruption layer).
 * <p>
 * 외부 정산 소스의 원본 표현(공백/대소문자/전각·중복 포함 JSON)을 도메인이 받아들일 수 있는
 * {@link IngestKnowledgeCommand} 로 번역한다: 정규화 · 도메인 기본값 · SHA-256 해시 · 필수 필드 검증.
 * 외부 표현의 변화가 도메인으로 새어 들어오지 못하게 막는 경계다.
 * <p>
 * 구현은 infrastructure({@code infrastructure.etl.SettlementSourceAclAdapter})에 둔다(정규화/해시 유틸이 거기 있음).
 */
public interface SettlementSourceAcl {

    /**
     * 외부 정산 소스 한 건을 도메인 적재 커맨드로 번역한다.
     * <p>
     * 필수 필드(title/body) 누락 시 {@link Optional#empty()} 를 반환한다(= 적재 skip).
     *
     * @param domain     원본 도메인 (공백/null 이면 기본값 SETTLEMENT)
     * @param title      원본 제목
     * @param body       원본 본문
     * @param sourceUrl  원본 출처 (null 허용)
     * @param codeValues 원본 코드값 묶음 (null 허용)
     */
    Optional<IngestKnowledgeCommand> toIngestCommand(
            String domain, String title, String body, String sourceUrl, Map<String, String> codeValues);
}
