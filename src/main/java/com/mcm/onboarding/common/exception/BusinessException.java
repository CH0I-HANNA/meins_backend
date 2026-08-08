package com.mcm.onboarding.common.exception;

import java.time.LocalDateTime;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    // 기획 명세상 특정 에러에만 실리는 부가 정보. 해당 없으면 null → 응답에서 키 자체가 빠진다.
    private final Integer remainingAttempts; // CODE_MISMATCH
    private final LocalDateTime lockedUntil;  // CODE_LOCKED
    private final LocalDateTime resetAt;      // CREDIT_EXHAUSTED

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null, null, null);
    }

    private BusinessException(ErrorCode errorCode, Integer remainingAttempts,
                              LocalDateTime lockedUntil, LocalDateTime resetAt) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.remainingAttempts = remainingAttempts;
        this.lockedUntil = lockedUntil;
        this.resetAt = resetAt;
    }

    // 프론트는 자체 카운터를 두지 않고 서버가 내려준 남은 횟수만 표시한다.
    public static BusinessException codeMismatch(int remainingAttempts) {
        return new BusinessException(ErrorCode.CODE_MISMATCH, remainingAttempts, null, null);
    }

    // 프론트가 "N시간 M분 후 다시 시도"를 계산할 수 있도록 해제 시각을 함께 내린다.
    public static BusinessException codeLocked(LocalDateTime lockedUntil) {
        return new BusinessException(ErrorCode.CODE_LOCKED, null, lockedUntil, null);
    }

    // resetAt은 크레딧 회복 정책이 확정되기 전까지 null로 나간다.
    public static BusinessException creditExhausted(LocalDateTime resetAt) {
        return new BusinessException(ErrorCode.CREDIT_EXHAUSTED, null, null, resetAt);
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public Integer getRemainingAttempts() { return remainingAttempts; }
    public LocalDateTime getLockedUntil() { return lockedUntil; }
    public LocalDateTime getResetAt() { return resetAt; }
}
