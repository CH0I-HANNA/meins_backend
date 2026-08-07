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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String tagCode;

    @Column(nullable = false)
    private int remaining = 30;

    private LocalDateTime updatedAt;

    public static ChatCredit init(String tagCode) {
        ChatCredit credit = new ChatCredit();
        credit.tagCode = tagCode;
        credit.remaining = 30;
        credit.updatedAt = LocalDateTime.now();
        return credit;
    }
}