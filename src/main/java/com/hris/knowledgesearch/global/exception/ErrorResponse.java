package com.hris.knowledgesearch.global.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.Instant;

/**
 * 에러 응답 본문.
 */
@Schema(description = "에러 응답")
@Getter
public class ErrorResponse {

    @Schema(description = "HTTP 상태 코드", example = "404")
    private final int status;

    @Schema(description = "에러 메시지", example = "지식 레코드를 찾을 수 없습니다.")
    private final String message;

    @Schema(description = "응답 생성 시간")
    private final Instant timestamp;

    private ErrorResponse(int status, String message) {
        this.status = status;
        this.message = message;
        this.timestamp = Instant.now();
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.getHttpStatus().value(), message);
    }
}
