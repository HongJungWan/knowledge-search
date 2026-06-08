package com.hris.knowledgesearch.infrastructure.etl;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

/**
 * ETL 입력 소스 항목 (classpath:sample/settlement-source.json).
 * <p>
 * 외부 소스에서 들어오는 원본 형태. 의도적으로 공백/대소문자 문제와 중복을 포함한 샘플을 받는다(PRD §7 검증).
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class SettlementSourceItem {

    private String domain;
    private String title;
    private String body;
    private String sourceUrl;
    private Map<String, String> codeValues;
}
