package com.mcm.onboarding.domain.chat.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_credits")
@Getter
@NoArgsConstructor
public class ChatCredit {

    // 1턴 = 1크레딧. 프론트는 remaining이 2 이하일 때 안내 문구를 띄운다.
    public static final int DEFAULT_LIMIT = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String tagCode;

    @Column(nullable = false)
    private int remaining = DEFAULT_LIMIT;

    private LocalDateTime updatedAt;

    public static ChatCredit init(String tagCode, LocalDateTime now) {
        ChatCredit credit = new ChatCredit();
        credit.tagCode = tagCode;
        credit.remaining = DEFAULT_LIMIT;
        credit.updatedAt = now;
        return credit;
    }
}
