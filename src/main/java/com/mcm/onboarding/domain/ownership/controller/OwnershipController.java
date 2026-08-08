package com.mcm.onboarding.domain.ownership.controller;

import com.mcm.onboarding.common.dto.ErrorResponse;
import com.mcm.onboarding.domain.ownership.dto.OwnershipRequest;
import com.mcm.onboarding.domain.ownership.dto.OwnershipResponse;
import com.mcm.onboarding.domain.ownership.service.OwnershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Ownership", description = "소유권 등록 및 오너 토큰 발급")
@RestController
@RequestMapping("/api/tags/{tagCode}/ownership")
@RequiredArgsConstructor
public class OwnershipController {

    private final OwnershipService ownershipService;

    @Operation(
        summary = "소유권 등록 (03)",
        description = """
            실물에 인쇄된 인증 코드를 검증해 소유 레코드를 생성하고 오너 토큰을 발급합니다. 인증 불필요.

            **잠금 정책**: 실패 누적과 잠금 판정은 서버가 `tagCode + ip_hash` 조합으로 관리합니다.
            5회 실패 시 24시간 잠금되며, 잠금 시각이 지나면 자동 해제됩니다.

            **응답에 실리는 부가 정보**
            - `CODE_MISMATCH` → `remainingAttempts` (프론트는 자체 카운터를 두지 말고 이 값만 표시)
            - `CODE_LOCKED` → `lockedUntil` (프론트가 "N시간 M분 후 다시 시도" 계산)

            이미 사용된 코드는 `CODE_MISMATCH`와 동일하게 처리됩니다 (사용 여부 노출 시 코드 열거 단서가 되므로).
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "소유권 등록 성공 — 토큰 및 소유 레코드 반환"),
        @ApiResponse(responseCode = "400", description = "인증 코드 불일치 (CODE_MISMATCH) / 태그 코드 형식 오류 (TAG_INVALID_FORMAT)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "존재하지 않는 태그 (TAG_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "이미 소유권 등록된 태그 (ALREADY_REGISTERED)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "시도 초과 잠금 (CODE_LOCKED)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<OwnershipResponse> registerOwnership(
        @Parameter(description = "QR 코드 값", example = "A1B2-C3D4") @PathVariable String tagCode,
        @Valid @RequestBody OwnershipRequest request,
        @Parameter(hidden = true) @RequestHeader(value = "X-Forwarded-For", defaultValue = "unknown") String clientIp
    ) {
        return ResponseEntity.ok(ownershipService.register(tagCode, request.code(), clientIp));
    }
}
