package com.mcm.onboarding.domain.ownership.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "소유권 등록 요청")
public record OwnershipRequest(
    @NotBlank
    @Schema(description = "실물 제품에 인쇄된 인증 코드", example = "7K2P9QXT4M8W")
    String authCode
) {}
