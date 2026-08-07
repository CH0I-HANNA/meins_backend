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
        summary = "소유권 등록 (3-2)",
        description = """
            QR 코드로 스캔한 제품에 대한 소유권을 등록합니다. 실물에 인쇄된 인증 코드(authCode)를
            함께 제출해야 하며, 서버가 DB에 저장된 값과 대조해 일치할 때만 등록됩니다. 인증 불필요(사전 토큰 없이 호출).

            **성공 시**: Bearer 토큰(`mcm:own:{tagCode}:{authCode}`)과 초기 크레딧(30) 발급

            **잠금 정책**: 이미 등록된 태그 재등록 시도 또는 인증 코드 불일치가 합산되어 5회 실패 시 잠금 (OWN_001)
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "소유권 등록 성공 — 토큰 및 크레딧 발급"),
        @ApiResponse(responseCode = "401", description = "인증 코드 불일치 (OWN_003)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "5회 초과 잠금 (OWN_001)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "존재하지 않는 태그 (TAG_001)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "이미 소유권 등록된 태그 (OWN_002)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<OwnershipResponse> registerOwnership(
        @Parameter(description = "QR 코드 값", example = "AB3D-9F2K") @PathVariable String tagCode,
        @Valid @RequestBody OwnershipRequest request,
        @Parameter(hidden = true) @RequestHeader(value = "X-Forwarded-For", defaultValue = "unknown") String clientIp
    ) {
        return ResponseEntity.ok(ownershipService.register(tagCode, request.authCode(), clientIp));
    }
}
