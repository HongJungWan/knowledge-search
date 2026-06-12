package com.hris.knowledgesearch.global.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.time.Instant;

/**
 * API 공통 응답 DTO.
 * <p>
 * 성공 응답을 {status, data, timestamp} 형식으로 일관되게 제공한다.
 */
@Schema(description = "API 공통 응답")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiResponse<T> {

    @Schema(description = "HTTP 상태 코드", example = "200")
    private int status;

    @Schema(description = "응답 데이터")
    private T data;

    @Schema(description = "응답 생성 시간", example = "2026-06-07T12:00:00Z")
    private Instant timestamp;

    private ApiResponse(int status, T data, Instant timestamp) {
        this.status = status;
        this.data = data;
        this.timestamp = timestamp;
    }

    /** 성공 응답 생성 (200 OK) */
    public static <T> ApiResponse<T> success(T data) {
        return of(HttpStatus.OK.value(), data);
    }

    /** 성공 응답 생성 (201 CREATED) */
    public static <T> ApiResponse<T> created(T data) {
        return of(HttpStatus.CREATED.value(), data);
    }

    /** 빈 성공 응답 생성 (200 OK, data = null) */
    public static <T> ApiResponse<T> success() {
        return of(HttpStatus.OK.value(), null);
    }

    /** 커스텀 상태 코드로 응답 생성 */
    public static <T> ApiResponse<T> of(HttpStatus status, T data) {
        return of(status.value(), data);
    }

    /** 커스텀 상태 코드로 응답 생성 (int) */
    public static <T> ApiResponse<T> of(int statusCode, T data) {
        return new ApiResponse<>(statusCode, data, Instant.now());
    }
}
