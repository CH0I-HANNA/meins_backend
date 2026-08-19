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

    private static final int TRANSFER_CODE_VALID_HOURS = 24;

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

    // 오너 토큰(mcm:own:{tagCode}:{ownerSecret})의 비밀 부분. 실물에 인쇄된 authCode와 분리되어 있어
    // 소유권 이전마다 재발급할 수 있고, 재발급되는 순간 이전 소유자가 캐시해둔 토큰은 자동 실효된다.
    @Column(length = 50)
    private String ownerSecret;

    // 발급된 소유권 이전 코드. null이면 활성 코드 없음.
    @Column(length = 50)
    private String transferCode;

    private LocalDateTime transferCodeIssuedAt;

    // 소유권이 이전된 누적 횟수. 이전 소유자 식별정보는 보관하지 않고 횟수만 남긴다.
    @Column(nullable = false)
    private int transferCount = 0;

    // 관리자 bulk-create 시 Tag 1건당 항상 미등록 상태로 함께 생성됨
    public static OwnershipRecord createFor(Tag tag) {
        OwnershipRecord record = new OwnershipRecord();
        record.tag = tag;
        return record;
    }

    public void markRegistered(String ipHash, LocalDateTime registeredAt, String ownerSecret) {
        this.ipHash = ipHash;
        this.registeredAt = registeredAt;
        this.ownerSecret = ownerSecret;
    }

    // 관리자 UNLOCK_RECOVERY / UNREGISTERED: 등록 이력 초기화 (재등록 가능 상태로)
    public void resetForRecovery() {
        this.ipHash = null;
        this.registeredAt = null;
        this.ownerSecret = null;
        this.transferCode = null;
        this.transferCodeIssuedAt = null;
    }

    public boolean hasActiveTransferCode(LocalDateTime now) {
        return transferCode != null && now.isBefore(transferCodeIssuedAt.plusHours(TRANSFER_CODE_VALID_HOURS));
    }

    public LocalDateTime getTransferCodeExpiresAt() {
        return transferCodeIssuedAt == null ? null : transferCodeIssuedAt.plusHours(TRANSFER_CODE_VALID_HOURS);
    }

    public void issueTransferCode(String code, LocalDateTime now) {
        this.transferCode = code;
        this.transferCodeIssuedAt = now;
    }

    public void cancelTransferCode() {
        this.transferCode = null;
        this.transferCodeIssuedAt = null;
    }

    // 소유권 이전 성공 처리: 새 소유자로 레코드를 교체한다.
    public void applyTransfer(String ipHash, LocalDateTime now, String newOwnerSecret) {
        this.ipHash = ipHash;
        this.registeredAt = now;
        this.ownerSecret = newOwnerSecret;
        this.transferCode = null;
        this.transferCodeIssuedAt = null;
        this.transferCount++;
    }
}
