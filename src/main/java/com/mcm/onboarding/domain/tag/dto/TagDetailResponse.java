package com.mcm.onboarding.domain.tag.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mcm.onboarding.common.util.KstTime;
import com.mcm.onboarding.domain.tag.entity.Tag;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// 기획 명세 2-1: GET /api/tags/{tagCode} 응답 (게스트, 인증 불필요).
// 구매처(purchasedFrom)는 8/2 회의에서 제외 결정 — 오너 응답에도 포함하지 않는다.
@Schema(description = "태그 조회 응답 (게스트)")
public record TagDetailResponse(
    @Schema(description = "QR 코드 값", example = "A1B2-C3D4") String tagCode,
    @Schema(description = "제품 정보") ProductInfo product,
    @Schema(description = "공식 출처 정보") OfficialInfo official,
    @Schema(description = "소유 등록 정보") OwnershipInfo ownership
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "소유 등록 정보 (게스트 정밀도)")
    public record OwnershipInfo(
        @Schema(description = "소유자 등록 여부", example = "true") boolean registered,
        @Schema(description = "소유 등록 시점 (YYYY-MM) — 미등록이면 null", example = "2026-03", nullable = true)
        String registeredAt
    ) {}

    public static TagDetailResponse of(Tag tag, LocalDateTime registeredAt) {
        return new TagDetailResponse(
            tag.getTagCode(),
            ProductInfo.from(tag.getProduct()),
            OfficialInfo.from(tag.getProduct()),
            // 게스트에게는 YYYY-MM까지만. 서버가 잘라서 내리므로 API를 직접 호출해도 분 단위는 알 수 없다.
            new OwnershipInfo(tag.isRegistered(), KstTime.toGuestPrecision(registeredAt))
        );
    }
}
