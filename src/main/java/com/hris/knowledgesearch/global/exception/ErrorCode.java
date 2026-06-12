package com.hris.knowledgesearch.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 비즈니스 에러 코드 정의.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common (400 / 500)
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "잘못된 입력값입니다."),
    INVALID_QUERY(HttpStatus.BAD_REQUEST, "검색 질의가 비어 있거나 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // Knowledge (404)
    RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "지식 레코드를 찾을 수 없습니다."),

    // ETL (400 / 500)
    ETL_TRIGGER_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ETL 적재 잡 실행에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
