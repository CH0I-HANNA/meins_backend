package com.mcm.onboarding.domain.ownership.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

// 기획 명세 2-2: body { "code": "F26T59QR9D3K" } — 하이픈 제거, 대문자 12자.
// 프론트가 정규화해서 보내지만 서버도 하이픈/소문자를 방어적으로 정규화한다.
@Schema(description = "소유권 등록 요청")
public record OwnershipRequest(
    @NotBlank
    @Schema(description = "실물 제품에 인쇄된 인증 코드 (12자, 0/O/1/I 제외)", example = "F26T59QR9D3K")
    String code
) {}
