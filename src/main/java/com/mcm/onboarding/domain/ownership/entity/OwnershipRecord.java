package com.mcm.onboarding.domain.ownership.entity;

import com.mcm.onboarding.domain.tag.entity.Tag;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 태그 1개당 1건인 "소유 레코드" 자체. 실패 카운트/잠금은 (태그, IP)별로 달라지므로
// OwnershipAttempt로 분리되어 있다.
@Entity
@Table(name = "ownership_records")
@Getter
@NoArgsConstructor
public class OwnershipRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false, unique = true)
    private Tag tag;

    // 등록을 완료한 주체의 IP 해시 (원본 미저장)
    @Column(length = 64)
    private String ipHash;

    private LocalDateTime registeredAt;

    // 관리자 bulk-create 시 Tag 1건당 항상 미등록 상태로 함께 생성됨
    public static OwnershipRecord createFor(Tag tag) {
        OwnershipRecord record = new OwnershipRecord();
        record.tag = tag;
        return record;
    }

    public void markRegistered(String ipHash, LocalDateTime registeredAt) {
        this.ipHash = ipHash;
        this.registeredAt = registeredAt;
    }

    // 관리자 UNLOCK_RECOVERY / UNREGISTERED: 등록 이력 초기화 (재등록 가능 상태로)
    public void resetForRecovery() {
        this.ipHash = null;
        this.registeredAt = null;
    }
}
