package com.mcm.onboarding.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // Auth
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_001", "유효하지 않은 토큰입니다."),
    TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "AUTH_002", "토큰의 tagCode가 요청과 일치하지 않습니다."),
    INVALID_ADMIN_KEY(HttpStatus.UNAUTHORIZED, "AUTH_003", "유효하지 않은 관리자 키입니다."),

    // Credit
    CREDIT_EXHAUSTED(HttpStatus.TOO_MANY_REQUESTS, "CREDIT_001", "크레딧이 소진되었습니다."),

    // Ownership
    OWNERSHIP_LOCKED(HttpStatus.FORBIDDEN, "OWN_001", "소유권 등록 시도 횟수를 초과하여 잠금 처리되었습니다."),
    OWNERSHIP_ALREADY_EXISTS(HttpStatus.CONFLICT, "OWN_002", "이미 소유권이 등록된 태그입니다."),
    INVALID_AUTH_CODE(HttpStatus.UNAUTHORIZED, "OWN_003", "인증 코드가 일치하지 않습니다."),

    // Tag
    TAG_NOT_FOUND(HttpStatus.NOT_FOUND, "TAG_001", "존재하지 않는 태그입니다."),

    // Admin
    INVALID_FORCE_STATUS_ACTION(HttpStatus.BAD_REQUEST, "ADMIN_001", "지원하지 않는 force-status action입니다."),

    // Server
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SERVER_001", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
}
