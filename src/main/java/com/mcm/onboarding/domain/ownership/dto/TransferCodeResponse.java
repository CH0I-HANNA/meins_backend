package com.mcm.onboarding.domain.ownership.dto;

import com.mcm.onboarding.common.util.KstTime;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "소유권 이전 코드 발급/조회 응답")
public record TransferCodeResponse(
    @Schema(description = "소유권 이전 코드 (12자, 0/O/1/I 제외) — 새 소유자에게 전달", example = "F26T59QR9D3K")
    String code,
    @Schema(description = "발급 시각 (ISO 8601, KST)", example = "2026-03-14T09:22:00+09:00") String issuedAt,
    @Schema(description = "만료 시각 (ISO 8601, KST) — 발급 후 24시간", example = "2026-03-15T09:22:00+09:00") String expiresAt
) {
    public static TransferCodeResponse of(String code, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        return new TransferCodeResponse(code, KstTime.toIso(issuedAt), KstTime.toIso(expiresAt));
    }
}
