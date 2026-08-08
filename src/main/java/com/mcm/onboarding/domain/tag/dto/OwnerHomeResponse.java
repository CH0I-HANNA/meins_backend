package com.mcm.onboarding.domain.tag.dto;

import com.mcm.onboarding.common.util.KstTime;
import com.mcm.onboarding.domain.tag.entity.Tag;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// 기획 명세 2-3: GET /api/tags/{tagCode}/ownership/me 응답 (오너 전용).
// 오너 홈은 게스트 뷰와 같은 정보 + 등록 정보 카드이므로 product/official을 함께 내려 호출 1회로 끝낸다.
@Schema(description = "오너 홈 응답 (소유 레코드 + 제품 정보)")
public record OwnerHomeResponse(
    @Schema(description = "소유 레코드") OwnerRecord record,
    @Schema(description = "제품 정보") ProductInfo product,
    @Schema(description = "공식 출처 정보") OfficialInfo official
) {
    @Schema(description = "소유 레코드 (오너 정밀도)")
    public record OwnerRecord(
        @Schema(description = "소유 등록 시점 (ISO 8601, KST)", example = "2026-03-14T09:22:00+09:00")
        String registeredAt
    ) {}

    public static OwnerHomeResponse of(Tag tag, LocalDateTime registeredAt) {
        return new OwnerHomeResponse(
            // 오너에게는 ISO 8601 전체 정밀도로 내리고, 표시 포맷은 프론트가 결정한다.
            new OwnerRecord(KstTime.toIso(registeredAt)),
            ProductInfo.from(tag.getProduct()),
            OfficialInfo.from(tag.getProduct())
        );
    }
}
