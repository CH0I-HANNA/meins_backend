package com.mcm.onboarding.domain.tag.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mcm.onboarding.domain.product.entity.Product;
import io.swagger.v3.oas.annotations.media.Schema;

// 기획 명세 2-1의 official 블록 — 공식 출처(제조연월 / 판매 등록).
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "공식 출처 정보")
public record OfficialInfo(
    @Schema(description = "제조연월 (YYYY-MM)", example = "2025-11", nullable = true) String manufacturedAt,
    @Schema(description = "판매 등록 연월 (YYYY-MM)", example = "2026-01", nullable = true) String releasedAt
) {
    public static OfficialInfo from(Product product) {
        return new OfficialInfo(product.getManufacturedYm(), product.getSaleRegisteredYm());
    }
}
