package com.mcm.onboarding.domain.tag.dto;

import com.mcm.onboarding.domain.product.entity.Product;
import com.mcm.onboarding.domain.tag.entity.Tag;
import com.mcm.onboarding.domain.tag.entity.TagStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "태그 + 제품 상세 정보")
public record TagDetailResponse(
    @Schema(description = "QR 코드 값", example = "AB3D-9F2K") String tagCode,
    @Schema(description = "제품명", example = "MCM 클래식 백팩") String productName,
    @Schema(description = "모델코드", example = "MMK6AVE20BK") String modelCode,
    @Schema(description = "제조연월", example = "2026-08") String manufacturedYm,
    @Schema(description = "소재", example = "비세토스 캔버스") String material,
    @Schema(description = "색상", example = "브라운") String color,
    @Schema(description = "판매 등록 연월", example = "2026-09") String saleRegisteredYm,
    @Schema(description = "가로 (cm)", example = "30") Integer widthCm,
    @Schema(description = "세로 (cm)", example = "15") Integer depthCm,
    @Schema(description = "높이 (cm)", example = "20") Integer heightCm,
    @Schema(description = "대표 이미지 URL") String imageUrl,
    @Schema(description = "하단 썸네일 이미지 URL 목록 (최대 3개)") List<String> thumbnailImageUrls,
    @Schema(description = "브랜드 공식 제품 페이지 URL") String productPageUrl,
    @Schema(description = "등록 상태", example = "REGISTERED") TagStatus status,
    @Schema(description = "소유 등록 시점 — 게스트 조회는 YYYY-MM, 오너 조회는 YYYY-MM-DD HH:mm 정밀도",
        example = "2026-08-06 20:00", nullable = true) String registeredAt
) {
    public static TagDetailResponse of(Tag tag, String registeredAt) {
        Product product = tag.getProduct();
        List<String> thumbnails = List.of(product.getThumbnailImageUrl1(), product.getThumbnailImageUrl2(),
                product.getThumbnailImageUrl3()).stream().filter(java.util.Objects::nonNull).toList();
        return new TagDetailResponse(
            tag.getTagCode(),
            product.getProductName(),
            product.getModelCode(),
            product.getManufacturedYm(),
            product.getMaterial(),
            product.getColor(),
            product.getSaleRegisteredYm(),
            product.getWidthCm(),
            product.getDepthCm(),
            product.getHeightCm(),
            product.getImageUrl(),
            thumbnails,
            product.getProductPageUrl(),
            tag.getStatus(),
            registeredAt
        );
    }
}
