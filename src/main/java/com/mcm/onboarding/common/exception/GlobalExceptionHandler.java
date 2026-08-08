package com.mcm.onboarding.common.exception;

import com.mcm.onboarding.common.dto.ErrorResponse;
import com.mcm.onboarding.common.util.KstTime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode ec = e.getErrorCode();
        return ResponseEntity.status(ec.getHttpStatus())
            .body(ErrorResponse.of(
                ec.getCode(),
                ec.getMessage(),
                e.getRemainingAttempts(),
                KstTime.toIso(e.getLockedUntil()),
                KstTime.toIso(e.getResetAt())
            ));
    }

    // Bean Validation 실패도 공통 포맷으로 내린다 (기본 Spring 응답은 { code, message, traceId } 형태가 아니므로).
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        ErrorCode ec = ErrorCode.CODE_MISMATCH;
        return ResponseEntity.status(ec.getHttpStatus())
            .body(ErrorResponse.of(ec.getCode(), ec.getMessage(), null, null, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        ErrorCode ec = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(ec.getHttpStatus())
            .body(ErrorResponse.of(ec.getCode(), ec.getMessage(), null, null, null));
    }
}
