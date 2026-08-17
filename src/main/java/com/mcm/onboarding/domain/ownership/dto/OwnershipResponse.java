package com.mcm.onboarding.domain.ownership.dto;

import com.mcm.onboarding.common.util.KstTime;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// 기획 명세 2-2 200 응답: { token, record: { registeredAt } }
// 구매처(purchasedFrom)는 8/2 회의에서 제외 결정.
@Schema(description = "소유권 등록 응답")
public record OwnershipResponse(
    @Schema(description = "오너 토큰 — 이후 오너 API에 Bearer로 사용. 태그 1개에만 묶인다.",
        example = "mcm:own:A1B2-C3D4:F26T59QR9D3K") String token,
    @Schema(description = "생성된 소유 레코드") RegisteredRecord record
) {
    @Schema(description = "소유 레코드 (오너 정밀도)")
    public record RegisteredRecord(
        @Schema(description = "소유 등록 시점 (ISO 8601, KST)", example = "2026-03-14T09:22:00+09:00")
        String registeredAt
    ) {}

    public static OwnershipResponse of(String tagCode, String ownerSecret, LocalDateTime registeredAt) {
        return new OwnershipResponse(
            "mcm:own:" + tagCode + ":" + ownerSecret,
            new RegisteredRecord(KstTime.toIso(registeredAt))
        );
    }
}
