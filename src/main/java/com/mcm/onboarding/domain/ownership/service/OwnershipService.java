package com.mcm.onboarding.domain.ownership.service;

import com.mcm.onboarding.common.exception.BusinessException;
import com.mcm.onboarding.common.exception.ErrorCode;
import com.mcm.onboarding.common.util.CodeNormalizer;
import com.mcm.onboarding.domain.chat.entity.ChatCredit;
import com.mcm.onboarding.domain.chat.repository.ChatCreditRepository;
import com.mcm.onboarding.domain.ownership.dto.OwnershipResponse;
import com.mcm.onboarding.domain.ownership.entity.OwnershipRecord;
import com.mcm.onboarding.domain.ownership.repository.OwnershipRepository;
import com.mcm.onboarding.domain.tag.entity.Tag;
import com.mcm.onboarding.domain.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class OwnershipService {

    private final TagRepository tagRepository;
    private final OwnershipRepository ownershipRepository;
    private final ChatCreditRepository chatCreditRepository;

    // tags/ownership_records는 관리자 bulk-create 시 1:1로 항상 함께 생성되므로
    // 여기서는 OwnershipRecord가 없는 상태(null)를 다루지 않는다.
    @Transactional(noRollbackFor = BusinessException.class)
    public OwnershipResponse register(String rawTagCode, String rawAuthCode, String clientIp) {
        String tagCode = CodeNormalizer.normalize(rawTagCode);
        String authCode = CodeNormalizer.normalize(rawAuthCode);

        Tag tag = tagRepository.findByTagCode(tagCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.TAG_NOT_FOUND));

        OwnershipRecord record = ownershipRepository.findByTag_TagCode(tagCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));

        // 잠금 확인 (가장 먼저)
        if (record.isLocked()) {
            throw new BusinessException(ErrorCode.OWNERSHIP_LOCKED);
        }

        // 이미 소유권 등록된 태그 — 실패 카운트 증가
        if (tag.isRegistered()) {
            failAndMaybeLock(record, ErrorCode.OWNERSHIP_ALREADY_EXISTS);
        }

        // 인증 코드 불일치 — 실물을 실제로 가진 사람만 등록 가능하도록 검증.
        // 브루트포스 방지를 위해 이미 등록된 태그 재시도와 같은 실패 카운트를 공유한다.
        if (!tag.getAuthCode().equals(authCode)) {
            failAndMaybeLock(record, ErrorCode.INVALID_AUTH_CODE);
        }

        // 소유권 등록 처리
        String ipHash = hashIp(clientIp);
        record.markRegistered(ipHash);
        ownershipRepository.save(record);

        tag.markRegistered();
        tagRepository.save(tag);

        // 크레딧 초기화 (중복 방지)
        ChatCredit credit = chatCreditRepository.findByTagCode(tagCode)
            .orElseGet(() -> ChatCredit.init(tagCode));
        chatCreditRepository.save(credit);

        return OwnershipResponse.of(tagCode, tag.getAuthCode(), credit.getRemaining());
    }

    // 실패 카운트 증가 후, 5회 도달 시 잠금 에러로 승격하고 그렇지 않으면 원래 에러를 던진다.
    private void failAndMaybeLock(OwnershipRecord record, ErrorCode originalError) {
        record.incrementFailure();
        ownershipRepository.save(record);

        if (record.isLocked()) {
            throw new BusinessException(ErrorCode.OWNERSHIP_LOCKED);
        }
        throw new BusinessException(originalError);
    }

    private String hashIp(String ip) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(ip.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return ip;
        }
    }
}
