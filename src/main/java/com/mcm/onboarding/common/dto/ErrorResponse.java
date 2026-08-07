package com.mcm.onboarding.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "공통 에러 응답")
public record ErrorResponse(
    @Schema(description = "에러 코드", example = "TAG_001") String code,
    @Schema(description = "에러 메시지", example = "존재하지 않는 태그입니다.") String message,
    @Schema(description = "요청 추적 ID (UUID)", example = "550e8400-e29b-41d4-a716-446655440000") String traceId
) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, UUID.randomUUID().toString());
    }
}
