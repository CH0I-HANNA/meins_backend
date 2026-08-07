package com.mcm.onboarding.domain.ownership.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "소유권 등록 응답")
public record OwnershipResponse(
    @Schema(description = "QR 코드 값", example = "AB3D-9F2K") String tagCode,
    @Schema(description = "Bearer 토큰 (이후 인증 필요 API에 사용)", example = "mcm:own:AB3D-9F2K:7K2P9QXT4M8W") String token,
    @Schema(description = "발급된 초기 크레딧 수", example = "30") int initialCredits
) {
    public static OwnershipResponse of(String tagCode, String authCode, int credits) {
        return new OwnershipResponse(tagCode, "mcm:own:" + tagCode + ":" + authCode, credits);
    }
}
