package com.mcm.onboarding.domain.ownership.entity;

import com.mcm.onboarding.domain.tag.entity.Tag;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ownership_records")
@Getter
@NoArgsConstructor
public class OwnershipRecord {

    private static final int MAX_FAILURES = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false, unique = true)
    private Tag tag;

    @Column(length = 64)
    private String ipHash;

    @Column(nullable = false)
    private int failureCount = 0;

    @Column(nullable = false)
    private boolean locked = false;

    private LocalDateTime registeredAt;

    // 관리자 bulk-create 시 Tag 1건당 항상 UNREGISTERED 상태로 함께 생성됨
    public static OwnershipRecord createFor(Tag tag) {
        OwnershipRecord record = new OwnershipRecord();
        record.tag = tag;
        return record;
    }

    // 5회 실패 시 잠금 — 4회까지는 OWN_002, 5회째부터 OWN_001
    public void incrementFailure() {
        this.failureCount++;
        if (this.failureCount >= MAX_FAILURES) {
            this.locked = true;
        }
    }

    public void markRegistered(String ipHash) {
        this.ipHash = ipHash;
        this.registeredAt = LocalDateTime.now();
        this.failureCount = 0; // 등록 성공 시 이전 실패 시도(브루트포스 등)는 리셋
    }

    // 관리자 UNLOCK: 잠금/실패 카운트만 초기화, 등록 상태는 유지
    public void unlock() {
        this.locked = false;
        this.failureCount = 0;
    }

    // 관리자 UNLOCK_RECOVERY: 잠금 해제 + 등록 이력 초기화 (재등록 가능 상태로)
    public void resetForRecovery() {
        this.locked = false;
        this.failureCount = 0;
        this.ipHash = null;
        this.registeredAt = null;
    }
}
