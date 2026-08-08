package com.mcm.onboarding.common.exception;

import com.mcm.onboarding.common.dto.ErrorResponse;
import com.mcm.onboarding.common.util.KstTime;
import org.springframework.dao.DataIntegrityViolationException;
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
    // 이 핸들러는 모든 @Valid 엔드포인트(소유권 등록뿐 아니라 관리자 API도)에 공통 적용되므로
    // 소유권 등록 전용 의미인 CODE_MISMATCH로 매핑하면 안 된다 — 일반 검증 실패 코드를 쓴다.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        ErrorCode ec = ErrorCode.VALIDATION_FAILED;
        return ResponseEntity.status(ec.getHttpStatus())
            .body(ErrorResponse.of(ec.getCode(), ec.getMessage(), null, null, null));
    }

    // 더블클릭/재시도로 같은 요청이 동시에 들어와 DB unique 제약(예: 소유권 시도 이력)에 걸린
    // 경우. 서버 결함이 아니라 재시도로 해결되는 상황이므로 500 대신 409로 명확히 구분한다.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        ErrorCode ec = ErrorCode.REQUEST_CONFLICT;
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
