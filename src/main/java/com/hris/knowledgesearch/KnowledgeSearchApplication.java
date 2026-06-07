package com.hris.knowledgesearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 사내 지식 검색(RAG) 백엔드 + MCP 서버 진입점.
 * <p>
 * Claude Code 가 MCP 도구 호출로 사내 정형 지식을 직접 검색한다. 검색 계층은 벡터 DB 없이
 * Redshift SQL 로 두고(운영 시), 로컬/기본 프로필은 H2 in-memory 로 무(無) AWS 부팅한다.
 * 자세한 설계는 {@code .claude/docs/prd-knowledge-search.md} 참조.
 */
@SpringBootApplication
public class KnowledgeSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeSearchApplication.class, args);
    }

}
