package com.mcm.onboarding.domain.tag.service;

import com.mcm.onboarding.common.exception.BusinessException;
import com.mcm.onboarding.common.exception.ErrorCode;
import com.mcm.onboarding.common.util.CodeNormalizer;
import com.mcm.onboarding.domain.ownership.repository.OwnershipRepository;
import com.mcm.onboarding.domain.tag.dto.TagDetailResponse;
import com.mcm.onboarding.domain.tag.entity.Tag;
import com.mcm.onboarding.domain.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class TagService {

    private static final DateTimeFormatter GUEST_PRECISION = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter OWNER_PRECISION = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final TagRepository tagRepository;
    private final OwnershipRepository ownershipRepository;

    // 3-1: 태그 기본 정보 조회 (인증 불필요) — 소유 등록 시점은 게스트 정밀도(YYYY-MM)로 마스킹
    public TagDetailResponse getTagInfo(String tagCode) {
        Tag tag = findTagOrThrow(tagCode);
        return TagDetailResponse.of(tag, formatRegisteredAt(tag, GUEST_PRECISION));
    }

    // 3-3: 오너 홈 정보 조회 (OwnerAuthInterceptor 통과 후 호출) — 오너에게는 전체 정밀도 노출
    public TagDetailResponse getOwnerHome(String tagCode) {
        Tag tag = findTagOrThrow(tagCode);
        return TagDetailResponse.of(tag, formatRegisteredAt(tag, OWNER_PRECISION));
    }

    private Tag findTagOrThrow(String tagCode) {
        return tagRepository.findByTagCode(CodeNormalizer.normalize(tagCode))
            .orElseThrow(() -> new BusinessException(ErrorCode.TAG_NOT_FOUND));
    }

    private String formatRegisteredAt(Tag tag, DateTimeFormatter formatter) {
        LocalDateTime registeredAt = ownershipRepository.findByTag_TagCode(tag.getTagCode())
            .map(record -> record.getRegisteredAt())
            .orElse(null);
        return registeredAt == null ? null : registeredAt.format(formatter);
    }
}
