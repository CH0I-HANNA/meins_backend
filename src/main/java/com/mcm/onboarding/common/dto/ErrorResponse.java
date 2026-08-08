package com.mcm.onboarding.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

// 기획 명세 1-2: 모든 실패 응답은 { code, message, traceId } 형태로 통일한다.
// remainingAttempts/lockedUntil/resetAt은 해당 에러에만 실리며, 없으면 키 자체가 빠진다.
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "공통 에러 응답")
public record ErrorResponse(
    @Schema(description = "에러 코드", example = "TAG_NOT_FOUND") String code,
    @Schema(description = "에러 메시지", example = "태그를 확인할 수 없습니다.") String message,
    @Schema(description = "요청 추적 ID (UUID) — 토스트에 노출하지 말고 console.error로만 남길 것",
        example = "550e8400-e29b-41d4-a716-446655440000") String traceId,
    @Schema(description = "CODE_MISMATCH일 때만 포함 — 잠금까지 남은 시도 횟수", example = "4", nullable = true)
    Integer remainingAttempts,
    @Schema(description = "CODE_LOCKED일 때만 포함 — 잠금 해제 시각 (ISO 8601, KST)",
        example = "2026-03-15T09:22:00+09:00", nullable = true) String lockedUntil,
    @Schema(description = "CREDIT_EXHAUSTED일 때만 포함 — 크레딧 회복 시각 (ISO 8601, KST)",
        example = "2026-03-14T12:22:00+09:00", nullable = true) String resetAt
) {
    public static ErrorResponse of(String code, String message,
                                   Integer remainingAttempts, String lockedUntil, String resetAt) {
        return new ErrorResponse(code, message, UUID.randomUUID().toString(),
            remainingAttempts, lockedUntil, resetAt);
    }
}
