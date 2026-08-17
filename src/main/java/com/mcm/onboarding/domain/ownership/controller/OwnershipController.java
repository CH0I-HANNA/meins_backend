package com.mcm.onboarding.domain.ownership.controller;

import com.mcm.onboarding.common.dto.ErrorResponse;
import com.mcm.onboarding.domain.ownership.dto.OwnershipRequest;
import com.mcm.onboarding.domain.ownership.dto.OwnershipResponse;
import com.mcm.onboarding.domain.ownership.dto.TransferCodeResponse;
import com.mcm.onboarding.domain.ownership.service.OwnershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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

    @Operation(
        summary = "소유권 이전 코드 발급/재조회 (05)",
        description = """
            새 소유자에게 전달할 이전 코드를 발급합니다. 오너 인증 필요.
            이미 발급된 활성 코드가 있으면 재발급하지 않고 그 코드를 그대로 반환합니다(모달 재진입 시 동일 코드 유지).
            발급 후 24시간이 지나면 자동 만료됩니다.
            """,
        security = @SecurityRequirement(name = "OwnerToken")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "발급/조회 성공"),
        @ApiResponse(responseCode = "401", description = "유효하지 않은 오너 토큰 (TOKEN_INVALID)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/transfer-code")
    public ResponseEntity<TransferCodeResponse> issueTransferCode(
        @Parameter(description = "QR 코드 값", example = "A1B2-C3D4") @PathVariable String tagCode
    ) {
        return ResponseEntity.ok(ownershipService.issueOrFetchTransferCode(tagCode));
    }

    @Operation(
        summary = "소유권 이전 코드 발급 취소 (05)",
        description = "발급된 이전 코드를 취소합니다. 오너 인증 필요. 활성 코드가 없어도 성공 처리됩니다.",
        security = @SecurityRequirement(name = "OwnerToken")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "취소 성공"),
        @ApiResponse(responseCode = "401", description = "유효하지 않은 오너 토큰 (TOKEN_INVALID)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/transfer-code")
    public ResponseEntity<Void> cancelTransferCode(
        @Parameter(description = "QR 코드 값", example = "A1B2-C3D4") @PathVariable String tagCode
    ) {
        ownershipService.cancelTransferCode(tagCode);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "소유권 이전받기",
        description = """
            판매자에게 전달받은 이전 코드를 검증해 소유권을 새 소유자에게 이전하고 새 오너 토큰을 발급합니다. 인증 불필요.

            이전 성공 시 이전 소유자의 토큰은 즉시 무효화되고(재발급된 ownerSecret과 더 이상 일치하지 않음),
            챗 이력은 승계되지 않으며 크레딧은 15턴으로 재발급됩니다.

            **잠금 정책**: 소유권 등록과 동일하게 `tagCode + ip_hash` 조합으로 5회 실패 시 24시간 잠금됩니다.
            만료/취소/불일치된 코드는 모두 CODE_MISMATCH로 동일하게 처리됩니다(활성 코드 여부 비노출).
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "이전 성공 — 새 토큰 및 소유 레코드 반환"),
        @ApiResponse(responseCode = "400", description = "이전 코드 불일치/만료/취소 (CODE_MISMATCH) / 태그 코드 형식 오류 (TAG_INVALID_FORMAT)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "존재하지 않는 태그 (TAG_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "시도 초과 잠금 (CODE_LOCKED)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/transfer")
    public ResponseEntity<OwnershipResponse> transferOwnership(
        @Parameter(description = "QR 코드 값", example = "A1B2-C3D4") @PathVariable String tagCode,
        @Valid @RequestBody OwnershipRequest request,
        @Parameter(hidden = true) @RequestHeader(value = "X-Forwarded-For", defaultValue = "unknown") String clientIp
    ) {
        return ResponseEntity.ok(ownershipService.transfer(tagCode, request.code(), clientIp));
    }
}
