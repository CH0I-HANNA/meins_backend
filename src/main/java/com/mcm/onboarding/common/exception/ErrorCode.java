package com.mcm.onboarding.common.exception;

import org.springframework.http.HttpStatus;

// 코드 문자열/HTTP 상태는 기획 명세 "API > 1-2. 에러 응답 포맷" 표를 그대로 따른다.
// 프론트가 이 code로 화면을 분기하므로 임의로 이름을 바꾸지 말 것.
public enum ErrorCode {

    // Tag — 세 가지 모두 프론트에서 "태그 확인 불가" 동일 화면으로 처리된다.
    // 원인 특정이 열거 단서가 되지 않도록 메시지도 동일하게 유지한다.
    TAG_NOT_FOUND(HttpStatus.NOT_FOUND, "TAG_NOT_FOUND", "태그를 확인할 수 없습니다."),
    TAG_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "TAG_INVALID_FORMAT", "태그를 확인할 수 없습니다."),

    // Ownership
    CODE_MISMATCH(HttpStatus.BAD_REQUEST, "CODE_MISMATCH", "인증 코드가 일치하지 않습니다."),
    CODE_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "CODE_LOCKED", "시도 횟수를 초과하여 일시적으로 잠금 처리되었습니다."),
    ALREADY_REGISTERED(HttpStatus.CONFLICT, "ALREADY_REGISTERED", "이미 소유권이 등록된 태그입니다."),

    // Auth — 토큰 없음/형식 오류/다른 태그의 토큰/미등록 상태를 모두 동일하게 처리한다.
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "유효하지 않은 토큰입니다."),

    // Chat
    CREDIT_EXHAUSTED(HttpStatus.TOO_MANY_REQUESTS, "CREDIT_EXHAUSTED", "크레딧이 소진되었습니다."),

    // Admin — 기획 명세 범위 밖(내부 운영 도구 전용)
    ADMIN_KEY_INVALID(HttpStatus.UNAUTHORIZED, "ADMIN_KEY_INVALID", "유효하지 않은 관리자 키입니다."),
    ADMIN_INVALID_ACTION(HttpStatus.BAD_REQUEST, "ADMIN_INVALID_ACTION", "지원하지 않는 force-status action입니다."),

    // 명세 범위 밖(모든 @Valid 엔드포인트 공통) — 소유권 등록의 CODE_MISMATCH(인증 코드 불일치, 비즈니스 로직)와는
    // 별개로, Bean Validation 자체가 실패했을 때(필수값 누락 등) 쓰는 일반 코드.
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "요청 값이 올바르지 않습니다."),

    // Server
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 내부 오류가 발생했습니다.");

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
