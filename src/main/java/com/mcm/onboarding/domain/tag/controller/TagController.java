package com.mcm.onboarding.domain.tag.controller;

import com.mcm.onboarding.common.dto.ErrorResponse;
import com.mcm.onboarding.domain.tag.dto.TagDetailResponse;
import com.mcm.onboarding.domain.tag.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Tag", description = "QR 코드 및 제품 정보 조회")
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @Operation(summary = "태그 조회 (3-1)", description = "tagCode로 제품 기본 정보를 조회합니다. 인증 불필요.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "존재하지 않는 태그 (TAG_001)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{tagCode}")
    public ResponseEntity<TagDetailResponse> getTag(
        @Parameter(description = "QR 코드 값", example = "AB3D-9F2K") @PathVariable String tagCode
    ) {
        return ResponseEntity.ok(tagService.getTagInfo(tagCode));
    }

    @Operation(summary = "오너 홈 조회 (3-3)", description = "소유자 전용 제품 정보 조회. Bearer 토큰 필수.",
        security = @SecurityRequirement(name = "OwnerToken"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "401", description = "토큰 없음 또는 tagCode 불일치 (AUTH_001 / AUTH_002)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "존재하지 않는 태그 (TAG_001)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{tagCode}/home")
    public ResponseEntity<TagDetailResponse> getOwnerHome(
        @Parameter(description = "QR 코드 값", example = "AB3D-9F2K") @PathVariable String tagCode
    ) {
        return ResponseEntity.ok(tagService.getOwnerHome(tagCode));
    }
}
