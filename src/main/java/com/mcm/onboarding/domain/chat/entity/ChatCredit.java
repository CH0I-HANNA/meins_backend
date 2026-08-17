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
    // 소유권 이전으로 새 소유자에게 크레딧이 재발급될 때의 한도
    public static final int TRANSFER_LIMIT = 15;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String tagCode;

    @Column(nullable = false)
    private int remaining = DEFAULT_LIMIT;

    // "LIMIT"은 MySQL 예약어라 컬럼명을 credit_limit으로 매핑
    @Column(name = "credit_limit", nullable = false)
    private int limit = DEFAULT_LIMIT;

    private LocalDateTime updatedAt;

    public static ChatCredit init(String tagCode, LocalDateTime now) {
        ChatCredit credit = new ChatCredit();
        credit.tagCode = tagCode;
        credit.remaining = DEFAULT_LIMIT;
        credit.limit = DEFAULT_LIMIT;
        credit.updatedAt = now;
        return credit;
    }

    // 소유권 이전 시 챗 이력은 승계되지 않고 크레딧도 축소된 한도로 재발급된다.
    public void resetForTransfer(LocalDateTime now) {
        this.remaining = TRANSFER_LIMIT;
        this.limit = TRANSFER_LIMIT;
        this.updatedAt = now;
    }
}
