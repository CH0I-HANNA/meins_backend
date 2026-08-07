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

    // Layer 1 가드레일: LLM 호출 전 반드시 먼저 호출
    public void checkCredit(String tagCode) {
        int remaining = chatCreditRepository.findRemainingByTagCode(tagCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.TAG_NOT_FOUND));
        if (remaining <= 0) {
            throw new BusinessException(ErrorCode.CREDIT_EXHAUSTED);
        }
    }

    // 스트림 종료/중단 양쪽에서 반드시 호출 (doOnComplete + doOnError)
    @Transactional
    public void deductCredit(String tagCode) {
        chatCreditRepository.decrementCredit(tagCode);
    }
}