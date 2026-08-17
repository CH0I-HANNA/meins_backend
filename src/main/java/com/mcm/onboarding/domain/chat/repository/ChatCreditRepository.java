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

    // 선차감한 크레딧을 되돌린다. remaining < c.limit 조건은 되돌리기가 중복 실행돼도
    // 지급 한도를 넘겨 크레딧이 불어나지 않게 하는 안전장치다. limit은 태그마다 다를 수 있어
    // (일반 30 / 소유권 이전 15) 외부 파라미터가 아니라 해당 행의 limit 컬럼을 직접 참조한다.
    @Modifying
    @Query("UPDATE ChatCredit c SET c.remaining = c.remaining + 1 WHERE c.tagCode = :tagCode AND c.remaining < c.limit")
    int incrementCredit(@Param("tagCode") String tagCode);

    void deleteByTagCode(String tagCode);
}
