package com.mcm.onboarding.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

// REGISTERED 액션만 새 오너 토큰을 발급하므로 그 외 액션은 token이 null → 응답에서 키 자체가 빠진다.
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "태그 상태 강제 변경 응답")
public record ForceStatusResponse(
    @Schema(description = "REGISTERED 액션으로 새로 발급된 오너 토큰. 그 외 액션에서는 필드 자체가 생략됨",
        example = "mcm:own:AB3D-9F2K:F26T59QR9D3K", nullable = true) String token
) {
    public static ForceStatusResponse withToken(String token) {
        return new ForceStatusResponse(token);
    }

    public static ForceStatusResponse empty() {
        return new ForceStatusResponse(null);
    }
}
