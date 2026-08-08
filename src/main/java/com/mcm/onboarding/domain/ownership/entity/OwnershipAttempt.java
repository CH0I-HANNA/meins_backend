package com.mcm.onboarding.domain.ownership.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 기획 명세 2-2 요청사항 2: 잠금 키는 tagCode 단독이 아니라 tagCode + ip_hash 조합.
// tagCode 단독으로 잠그면 아무나 남의 태그에 아무 코드나 5번 쳐서 24시간 잠글 수 있다.
// 소유 레코드(OwnershipRecord)와 분리한 이유: 레코드는 태그당 1건이지만 시도 이력은 (태그, IP)당 1건이다.
@Entity
@Table(
    name = "ownership_attempts",
    uniqueConstraints = @UniqueConstraint(name = "uk_attempt_tag_ip", columnNames = {"tag_code", "ip_hash"})
)
@Getter
@NoArgsConstructor
public class OwnershipAttempt {

    private static final int MAX_FAILURES = 5;
    private static final int LOCK_HOURS = 24;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tag_code", nullable = false, length = 50)
    private String tagCode;

    // IP 원본은 저장하지 않고 SHA-256 해시만 보관한다 (명세 2-2 요청사항 5).
    @Column(name = "ip_hash", nullable = false, length = 64)
    private String ipHash;

    @Column(nullable = false)
    private int failureCount = 0;

    // null이면 잠금 없음. 이 시각이 지나면 자동으로 풀린다 (5회 실패 → 24시간 잠금).
    private LocalDateTime lockedUntil;

    private LocalDateTime updatedAt;

    public static OwnershipAttempt of(String tagCode, String ipHash) {
        OwnershipAttempt attempt = new OwnershipAttempt();
        attempt.tagCode = tagCode;
        attempt.ipHash = ipHash;
        return attempt;
    }

    public boolean isLockedAt(LocalDateTime now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    // 잠금 기간이 지났으면 카운트까지 초기화해 처음부터 다시 시도할 수 있게 한다.
    public void clearExpiredLock(LocalDateTime now) {
        if (lockedUntil != null && !now.isBefore(lockedUntil)) {
            this.failureCount = 0;
            this.lockedUntil = null;
        }
    }

    public void incrementFailure(LocalDateTime now) {
        this.failureCount++;
        this.updatedAt = now;
        if (this.failureCount >= MAX_FAILURES) {
            this.lockedUntil = now.plusHours(LOCK_HOURS);
        }
    }

    // 프론트는 자체 카운터를 두지 않고 이 값만 표시한다.
    public int remainingAttempts() {
        return Math.max(0, MAX_FAILURES - failureCount);
    }

    public void reset(LocalDateTime now) {
        this.failureCount = 0;
        this.lockedUntil = null;
        this.updatedAt = now;
    }
}
