package com.mcm.onboarding.domain.ownership.service;

import com.mcm.onboarding.common.exception.BusinessException;
import com.mcm.onboarding.common.exception.ErrorCode;
import com.mcm.onboarding.common.util.CodeNormalizer;
import com.mcm.onboarding.common.util.KstTime;
import com.mcm.onboarding.domain.chat.entity.ChatCredit;
import com.mcm.onboarding.domain.chat.repository.ChatCreditRepository;
import com.mcm.onboarding.domain.ownership.dto.OwnershipResponse;
import com.mcm.onboarding.domain.ownership.entity.OwnershipAttempt;
import com.mcm.onboarding.domain.ownership.entity.OwnershipRecord;
import com.mcm.onboarding.domain.ownership.repository.OwnershipAttemptRepository;
import com.mcm.onboarding.domain.ownership.repository.OwnershipRepository;
import com.mcm.onboarding.domain.tag.entity.Tag;
import com.mcm.onboarding.domain.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class OwnershipService {

    private final TagRepository tagRepository;
    private final OwnershipRepository ownershipRepository;
    private final OwnershipAttemptRepository ownershipAttemptRepository;
    private final ChatCreditRepository chatCreditRepository;

    // tags/ownership_records는 관리자 bulk-create 시 1:1로 항상 함께 생성되므로
    // 여기서는 OwnershipRecord가 없는 상태(null)를 다루지 않는다.
    @Transactional(noRollbackFor = BusinessException.class)
    public OwnershipResponse register(String rawTagCode, String rawCode, String clientIp) {
        String tagCode = CodeNormalizer.normalizeAndValidateTagCode(rawTagCode);
        String authCode = CodeNormalizer.normalizeAuthCode(rawCode);
        LocalDateTime now = KstTime.now();
        String ipHash = hashIp(clientIp);

        Tag tag = tagRepository.findByTagCode(tagCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.TAG_NOT_FOUND));

        OwnershipRecord record = ownershipRepository.findByTag_TagCode(tagCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));

        // 실패 누적과 잠금 판정은 (태그, IP) 단위로 서버가 관리한다.
        // 프론트 세션 기준이면 브라우저를 닫거나 시크릿 창을 열어 우회할 수 있고,
        // tagCode 단독이면 제3자가 남의 태그를 골라 잠글 수 있다.
        OwnershipAttempt attempt = ownershipAttemptRepository.findByTagCodeAndIpHash(tagCode, ipHash)
            .orElseGet(() -> OwnershipAttempt.of(tagCode, ipHash));

        attempt.clearExpiredLock(now); // 24시간 경과분은 자동 해제
        if (attempt.isLockedAt(now)) {
            ownershipAttemptRepository.save(attempt);
            throw BusinessException.codeLocked(attempt.getLockedUntil());
        }

        // 이미 소유권 등록된 태그
        if (tag.isRegistered()) {
            failAndMaybeLock(attempt, now, ErrorCode.ALREADY_REGISTERED);
        }

        // 인증 코드 불일치 — 실물을 실제로 가진 사람만 등록 가능하도록 검증.
        // 이미 사용된 코드도 여기서 동일하게 걸리므로 "사용 여부"가 노출되지 않는다(코드 열거 방지).
        if (!tag.getAuthCode().equals(authCode)) {
            failAndMaybeLock(attempt, now, ErrorCode.CODE_MISMATCH);
        }

        // 소유권 등록 처리
        record.markRegistered(ipHash, now);
        ownershipRepository.save(record);

        attempt.reset(now);
        ownershipAttemptRepository.save(attempt);

        tag.markRegistered();
        tagRepository.save(tag);

        // 크레딧 초기화 (중복 방지)
        ChatCredit credit = chatCreditRepository.findByTagCode(tagCode)
            .orElseGet(() -> ChatCredit.init(tagCode, now));
        chatCreditRepository.save(credit);

        return OwnershipResponse.of(tagCode, tag.getAuthCode(), record.getRegisteredAt());
    }

    // 실패 카운트 증가 후, 5회 도달 시 CODE_LOCKED로 승격하고 그렇지 않으면 원래 에러를 던진다.
    private void failAndMaybeLock(OwnershipAttempt attempt, LocalDateTime now, ErrorCode originalError) {
        attempt.incrementFailure(now);
        ownershipAttemptRepository.save(attempt);

        if (attempt.isLockedAt(now)) {
            throw BusinessException.codeLocked(attempt.getLockedUntil());
        }
        if (originalError == ErrorCode.CODE_MISMATCH) {
            throw BusinessException.codeMismatch(attempt.remainingAttempts());
        }
        throw new BusinessException(originalError);
    }

    private String hashIp(String rawIp) {
        // X-Forwarded-For는 "client, proxy1, proxy2" 형태로 올 수 있다. 전체 문자열을 그대로 해싱하면
        // 프록시 체인 길이/공백 차이만으로 같은 클라이언트가 다른 해시를 갖게 되어 잠금 키가 불안정해진다.
        String ip = rawIp.split(",")[0].trim();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(ip.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return ip;
        }
    }
}
