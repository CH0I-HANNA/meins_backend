package com.mcm.onboarding.domain.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 챗 요청 — message 또는 preset 중 하나만 전송")
public record ChatRequest(
    @Schema(description = "자유 문의 메시지", example = "이 가방 어떻게 관리하나요?", nullable = true)
    String message,

    @Schema(description = "프리셋 유형 (care | style | heritage)", example = "care",
        allowableValues = {"care", "style", "heritage"}, nullable = true)
    String preset
) {}
