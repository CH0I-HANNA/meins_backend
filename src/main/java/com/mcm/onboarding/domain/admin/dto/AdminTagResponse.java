package com.mcm.onboarding.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mcm.onboarding.common.util.KstTime;
import com.mcm.onboarding.domain.ownership.entity.OwnershipRecord;
import com.mcm.onboarding.domain.tag.entity.Tag;
import com.mcm.onboarding.domain.tag.entity.TagStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "관리자용 태그 목록 항목")
public record AdminTagResponse(
    @Schema(description = "QR 코드 값", example = "AB3D-9F2K") String tagCode,
    @Schema(description = "실물 인쇄용 인증 코드", example = "7K2P9QXT4M8W") String authCode,
    @Schema(description = "등록 상태", example = "UNREGISTERED") TagStatus status,
    @Schema(description = "잠금 여부", example = "false") boolean locked,
    @Schema(description = "제품명", example = "MCM 클래식 백팩") String productName,
    @Schema(description = "QR 이미지 조회 URL", example = "/admin/qr/AB3D-9F2K") String qrImageUrl,
    @Schema(description = "누적 소유권 이전 횟수", example = "0") int transferCount,
    @Schema(description = "현재 활성 소유권 이전 코드 존재 여부", example = "false") boolean hasActiveTransferCode,
    @Schema(description = "활성 이전 코드 만료 시각 (ISO 8601, KST) — 활성 코드 없으면 생략",
        example = "2026-03-15T09:22:00+09:00", nullable = true) String transferCodeExpiresAt
) {
    public static AdminTagResponse of(Tag tag, boolean locked, OwnershipRecord record, LocalDateTime now) {
        boolean hasActiveTransferCode = record.hasActiveTransferCode(now);
        return new AdminTagResponse(
            tag.getTagCode(),
            tag.getAuthCode(),
            tag.getStatus(),
            locked,
            tag.getProduct().getProductName(),
            "/admin/qr/" + tag.getTagCode(),
            record.getTransferCount(),
            hasActiveTransferCode,
            hasActiveTransferCode ? KstTime.toIso(record.getTransferCodeExpiresAt()) : null
        );
    }
}
