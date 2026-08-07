package com.mcm.onboarding.domain.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "제품 등록 + 태그 일괄 생성 응답")
public record BulkCreateResponse(
    @Schema(description = "생성된 제품 ID", example = "1") Long productId,
    @Schema(description = "생성된 태그 목록") List<CreatedTag> tags
) {
    @Schema(description = "생성된 태그 1건 (tagCode/authCode)")
    public record CreatedTag(
        @Schema(description = "QR 코드 값", example = "AB3D-9F2K") String tagCode,
        @Schema(description = "실물 인쇄용 인증 코드", example = "7K2P9QXT4M8W") String authCode
    ) {}
}
