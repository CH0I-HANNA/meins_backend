package com.mcm.onboarding.domain.ownership.repository;

import com.mcm.onboarding.domain.ownership.entity.OwnershipRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OwnershipRepository extends JpaRepository<OwnershipRecord, Long> {

    Optional<OwnershipRecord> findByTag_TagCode(String tagCode);

    void deleteByTag_TagCode(String tagCode);
}
