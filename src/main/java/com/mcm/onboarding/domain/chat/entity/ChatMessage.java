package com.mcm.onboarding.domain.chat.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Getter
@NoArgsConstructor
@Schema(description = "챗 메시지 1건 (히스토리 조회 응답 배열의 원소)")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "메시지 ID", example = "1")
    private Long id;

    @Column(nullable = false, length = 50)
    @Schema(description = "QR 코드 값", example = "AB3D-9F2K")
    private String tagCode;

    @Column(nullable = false, length = 10)
    @Schema(description = "발화 주체", example = "user", allowableValues = {"user", "assistant"})
    private String role; // "user" | "assistant"

    @Lob
    @Column(nullable = false)
    @Schema(description = "메시지 본문. role=assistant인 경우 스트리밍이 끝난 뒤 저장된 전체 응답 텍스트",
        example = "이 가방은 비세토스 캔버스 소재로, 물기가 묻으면 마른 천으로 바로 닦아주세요.")
    private String content;

    @Column(length = 20)
    @Schema(description = "요청 시 사용된 프리셋. 자유 문의(message)였다면 null",
        example = "care", allowableValues = {"care", "style", "heritage"}, nullable = true)
    private String preset; // "care" | "style" | "heritage" | null

    @Column(nullable = false)
    @Schema(description = "메시지 생성 시각", example = "2026-08-06T20:00:00")
    private LocalDateTime createdAt;

    public static ChatMessage of(String tagCode, String role, String content, String preset) {
        ChatMessage msg = new ChatMessage();
        msg.tagCode = tagCode;
        msg.role = role;
        msg.content = content;
        msg.preset = preset;
        msg.createdAt = LocalDateTime.now();
        return msg;
    }
}
