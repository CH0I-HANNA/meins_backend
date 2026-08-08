package com.mcm.onboarding.domain.tag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

// 기획 명세: 가로 * 세로 * 높이 cm (정수)
@Schema(description = "제품 사이즈 (cm, 정수)")
public record SizeInfo(
    @Schema(description = "가로", example = "30") Integer width,
    @Schema(description = "세로", example = "12") Integer depth,
    @Schema(description = "높이", example = "22") Integer height
) {
    // 세 값이 모두 없으면 size 키 자체를 내리지 않는다.
    public static SizeInfo from(Integer width, Integer depth, Integer height) {
        if (width == null && depth == null && height == null) {
            return null;
        }
        return new SizeInfo(width, depth, height);
    }
}
