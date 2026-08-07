package com.mcm.onboarding.domain.chat.repository;

import com.mcm.onboarding.domain.chat.entity.ChatCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatCreditRepository extends JpaRepository<ChatCredit, Long> {

    Optional<ChatCredit> findByTagCode(String tagCode);

    @Query("SELECT c.remaining FROM ChatCredit c WHERE c.tagCode = :tagCode")
    Optional<Integer> findRemainingByTagCode(@Param("tagCode") String tagCode);

    @Modifying
    @Query("UPDATE ChatCredit c SET c.remaining = c.remaining - 1 WHERE c.tagCode = :tagCode AND c.remaining > 0")
    int decrementCredit(@Param("tagCode") String tagCode);

    void deleteByTagCode(String tagCode);
}
