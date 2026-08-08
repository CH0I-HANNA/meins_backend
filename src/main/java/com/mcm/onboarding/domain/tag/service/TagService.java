package com.mcm.onboarding.domain.tag.service;

import com.mcm.onboarding.common.exception.BusinessException;
import com.mcm.onboarding.common.exception.ErrorCode;
import com.mcm.onboarding.common.util.CodeNormalizer;
import com.mcm.onboarding.domain.ownership.entity.OwnershipRecord;
import com.mcm.onboarding.domain.ownership.repository.OwnershipRepository;
import com.mcm.onboarding.domain.tag.dto.OwnerHomeResponse;
import com.mcm.onboarding.domain.tag.dto.TagDetailResponse;
import com.mcm.onboarding.domain.tag.entity.Tag;
import com.mcm.onboarding.domain.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;
    private final OwnershipRepository ownershipRepository;

    // 2-1: 태그 조회 (인증 불필요) — 소유 등록 시점은 게스트 정밀도(YYYY-MM)로 마스킹해서 내린다.
    public TagDetailResponse getTagInfo(String tagCode) {
        Tag tag = findTagOrThrow(tagCode);
        return TagDetailResponse.of(tag, findRegisteredAt(tag));
    }

    // 2-3: 오너 홈 (OwnerAuthInterceptor 통과 후 호출) — 오너에게는 ISO 8601 전체 정밀도를 내린다.
    public OwnerHomeResponse getOwnerHome(String tagCode) {
        Tag tag = findTagOrThrow(tagCode);
        return OwnerHomeResponse.of(tag, findRegisteredAt(tag));
    }

    private Tag findTagOrThrow(String rawTagCode) {
        // 형식 검증을 먼저 — 형식 오류는 DB 조회 전에 TAG_INVALID_FORMAT(400)으로 끊는다.
        String tagCode = CodeNormalizer.normalizeAndValidateTagCode(rawTagCode);
        return tagRepository.findByTagCode(tagCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.TAG_NOT_FOUND));
    }

    private LocalDateTime findRegisteredAt(Tag tag) {
        return ownershipRepository.findByTag_TagCode(tag.getTagCode())
            .map(OwnershipRecord::getRegisteredAt)
            .orElse(null);
    }
}
