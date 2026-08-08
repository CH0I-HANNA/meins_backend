package com.mcm.onboarding.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mcm.onboarding.common.util.KstTime;
import com.mcm.onboarding.domain.chat.entity.ChatMessage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// 기획 명세 2-4: { messages: [...], credits: { remaining, limit, resetAt } }
@Schema(description = "챗 히스토리 + 크레딧 잔량")
public record ChatHistoryResponse(
    @Schema(description = "대화 내역 (시간순)") List<Message> messages,
    @Schema(description = "크레딧 잔량") Credits credits
) {
    @Schema(description = "메시지 1건")
    public record Message(
        @Schema(description = "발화 주체", example = "assistant", allowableValues = {"user", "assistant"})
        String role,
        @Schema(description = "메시지 본문") String content,
        @Schema(description = "생성 시각 (ISO 8601, KST)", example = "2026-03-14T09:22:00+09:00") String createdAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "크레딧 잔량")
    public record Credits(
        @Schema(description = "남은 크레딧", example = "7") int remaining,
        @Schema(description = "총 크레딧", example = "30") int limit,
        @Schema(description = "회복 시각 (ISO 8601, KST). 회복 정책 미확정이라 현재는 항상 생략됨",
            example = "2026-03-14T12:22:00+09:00", nullable = true) String resetAt
    ) {}

    public static ChatHistoryResponse of(List<ChatMessage> messages, int remaining, int limit) {
        List<Message> mapped = messages.stream()
            .map(m -> new Message(m.getRole(), m.getContent(), KstTime.toIso(m.getCreatedAt())))
            .toList();
        // resetAt: 크레딧 회복 정책(롤링 리셋 주기)이 확정되면 채운다.
        return new ChatHistoryResponse(mapped, new Credits(remaining, limit, null));
    }
}
