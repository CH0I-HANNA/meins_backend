package com.mcm.onboarding.domain.tag.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mcm.onboarding.domain.product.entity.Product;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Objects;

// 기획 명세 2-1의 product 블록.
// material/size/color/productUrl은 값이 없으면 키 자체가 빠진다 → 프론트가 해당 행/버튼을 숨긴다.
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "제품 정보")
public record ProductInfo(
    @Schema(description = "제품명", example = "MCM 클래식 백팩") String name,
    @Schema(description = "모델코드", example = "MMK-AA1234") String modelCode,
    @Schema(description = "대표 이미지 URL") String heroImage,
    @Schema(description = "상세 이미지 URL 목록 (0~3개)") List<String> detailImages,
    @Schema(description = "소재", example = "코티드 캔버스", nullable = true) String material,
    @Schema(description = "사이즈 (cm)", nullable = true) SizeInfo size,
    @Schema(description = "색상", example = "코냑", nullable = true) String color,
    @Schema(description = "브랜드 공식 제품 페이지 URL", nullable = true) String productUrl
) {
    public static ProductInfo from(Product product) {
        List<String> detailImages = java.util.stream.Stream.of(
                product.getThumbnailImageUrl1(),
                product.getThumbnailImageUrl2(),
                product.getThumbnailImageUrl3())
            .filter(Objects::nonNull)
            .toList();

        return new ProductInfo(
            product.getProductName(),
            product.getModelCode(),
            product.getImageUrl(),
            detailImages,
            product.getMaterial(),
            SizeInfo.from(product.getWidthCm(), product.getDepthCm(), product.getHeightCm()),
            product.getColor(),
            product.getProductPageUrl()
        );
    }
}
