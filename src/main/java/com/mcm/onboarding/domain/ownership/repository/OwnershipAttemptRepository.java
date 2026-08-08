package com.mcm.onboarding.domain.ownership.repository;

import com.mcm.onboarding.domain.ownership.entity.OwnershipAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OwnershipAttemptRepository extends JpaRepository<OwnershipAttempt, Long> {

    Optional<OwnershipAttempt> findByTagCodeAndIpHash(String tagCode, String ipHash);

    // 관리자 목록에서 "이 태그를 지금 잠긴 상태로 보고 있는 IP가 하나라도 있는지" 판정용
    boolean existsByTagCodeAndLockedUntilAfter(String tagCode, LocalDateTime now);

    void deleteByTagCode(String tagCode);
}
