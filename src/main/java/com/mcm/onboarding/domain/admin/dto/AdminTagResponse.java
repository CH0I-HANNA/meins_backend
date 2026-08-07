package com.mcm.onboarding.domain.admin.dto;

import com.mcm.onboarding.domain.tag.entity.Tag;
import com.mcm.onboarding.domain.tag.entity.TagStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자용 태그 목록 항목")
public record AdminTagResponse(
    @Schema(description = "QR 코드 값", example = "AB3D-9F2K") String tagCode,
    @Schema(description = "실물 인쇄용 인증 코드", example = "7K2P9QXT4M8W") String authCode,
    @Schema(description = "등록 상태", example = "UNREGISTERED") TagStatus status,
    @Schema(description = "잠금 여부", example = "false") boolean locked,
    @Schema(description = "제품명", example = "MCM 클래식 백팩") String productName,
    @Schema(description = "QR 이미지 조회 URL", example = "/admin/qr/AB3D-9F2K") String qrImageUrl
) {
    public static AdminTagResponse of(Tag tag, boolean locked) {
        return new AdminTagResponse(
            tag.getTagCode(),
            tag.getAuthCode(),
            tag.getStatus(),
            locked,
            tag.getProduct().getProductName(),
            "/admin/qr/" + tag.getTagCode()
        );
    }
}
