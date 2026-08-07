package com.mcm.onboarding.domain.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "제품 등록 + 태그 일괄 생성 요청")
public record BulkCreateRequest(
    @NotBlank @Schema(description = "제품명", example = "MCM 클래식 백팩") String productName,
    @Schema(description = "모델코드", example = "MMK6AVE20BK") String modelCode,
    @Schema(description = "제조연월 (YYYY-MM)", example = "2026-08") String manufacturedYm,
    @Schema(description = "소재", example = "비세토스 캔버스") String material,
    @Schema(description = "색상", example = "브라운") String color,
    @Schema(description = "판매 등록 연월 (YYYY-MM)", example = "2026-09") String saleRegisteredYm,
    @Schema(description = "가로 (cm)", example = "30") Integer widthCm,
    @Schema(description = "세로 (cm)", example = "15") Integer depthCm,
    @Schema(description = "높이 (cm)", example = "20") Integer heightCm,
    @Schema(description = "대표 이미지 URL (이미 호스팅된 이미지 링크)", example = "https://cdn.mcm.com/products/A1B2/main.jpg") String imageUrl,
    @Schema(description = "하단 썸네일 이미지 URL 1", example = "https://cdn.mcm.com/products/A1B2/thumb1.jpg") String thumbnailImageUrl1,
    @Schema(description = "하단 썸네일 이미지 URL 2", example = "https://cdn.mcm.com/products/A1B2/thumb2.jpg") String thumbnailImageUrl2,
    @Schema(description = "하단 썸네일 이미지 URL 3", example = "https://cdn.mcm.com/products/A1B2/thumb3.jpg") String thumbnailImageUrl3,
    @Schema(description = "브랜드 공식 제품 페이지 URL", example = "https://www.mcm.com/kr/products/A1B2") String productPageUrl,
    @Min(1) @Max(1000) @Schema(description = "생성할 태그(QR) 수량", example = "50") int quantity
) {}
