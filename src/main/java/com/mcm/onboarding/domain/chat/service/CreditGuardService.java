package com.mcm.onboarding.domain.chat.service;

import com.mcm.onboarding.common.exception.BusinessException;
import com.mcm.onboarding.common.exception.ErrorCode;
import com.mcm.onboarding.domain.chat.repository.ChatCreditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreditGuardService {

    private final ChatCreditRepository chatCreditRepository;

    // Layer 1 가드레일: LLM 호출 전 반드시 먼저 호출.
    // 소진 시 LLM을 호출하지 않고 429로 끊는다 — 안내 문구는 프론트가 하드코딩한다.
    // 실제 차감 게이트는 reserveCredit()이고, 이건 태그 존재 여부(TAG_NOT_FOUND)를 가리고
    // 이미 소진된 걸 미리 알 때 원자적 UPDATE 시도조차 하지 않기 위한 빠른 실패 경로다.
    public void checkCredit(String tagCode) {
        int remaining = chatCreditRepository.findRemainingByTagCode(tagCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.TAG_NOT_FOUND));
        if (remaining <= 0) {
            // resetAt: 크레딧 회복 정책이 확정되면 채운다.
            throw BusinessException.creditExhausted(null);
        }
    }

    // LLM 호출 직전에 반드시 호출해 원자적으로 1턴을 선차감한다. 조회(checkCredit)와 차감이
    // 분리돼 있으면 동시 요청 둘 다 조회를 통과해 LLM을 이중 호출할 수 있어, "UPDATE ... WHERE
    // remaining > 0"의 영향받은 row 수 자체로 성공/실패를 판정한다 — 이 안에서 레이스가 닫힌다.
    @Transactional
    public void reserveCredit(String tagCode) {
        int updated = chatCreditRepository.decrementCredit(tagCode);
        if (updated == 0) {
            throw BusinessException.creditExhausted(null);
        }
    }
}