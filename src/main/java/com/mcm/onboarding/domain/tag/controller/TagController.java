package com.mcm.onboarding.domain.tag.controller;

import com.mcm.onboarding.common.dto.ErrorResponse;
import com.mcm.onboarding.domain.tag.dto.OwnerHomeResponse;
import com.mcm.onboarding.domain.tag.dto.TagDetailResponse;
import com.mcm.onboarding.domain.tag.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Tag", description = "QR 코드 및 제품 정보 조회")
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @Operation(summary = "태그 조회 (01 → 02)", description = """
        tagCode로 제품 정보 + 공식 출처 + 소유 등록 여부를 한 번에 조회합니다. 인증 불필요.

        `ownership.registeredAt`은 게스트 정밀도(`YYYY-MM`)로만 내려갑니다.
        `material`/`size`/`color`/`productUrl`은 값이 없으면 키 자체가 응답에서 빠집니다.
        """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "400", description = "태그 코드 형식 오류 (TAG_INVALID_FORMAT)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "존재하지 않는 태그 (TAG_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{tagCode}")
    public ResponseEntity<TagDetailResponse> getTag(
        @Parameter(description = "QR 코드 값", example = "A1B2-C3D4") @PathVariable String tagCode
    ) {
        return ResponseEntity.ok(tagService.getTagInfo(tagCode));
    }

    @Operation(summary = "토큰 검증 / 소유 레코드 조회 (04)", description = """
        소유자 전용. Bearer 토큰 필수.

        오너 홈은 게스트 뷰와 같은 정보 + 등록 정보 카드이므로 `product`/`official`을 함께 내려 호출 1회로 끝냅니다.
        `record.registeredAt`은 ISO 8601(KST 오프셋) 전체 정밀도입니다.
        """, security = @SecurityRequirement(name = "OwnerToken"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "401", description = "토큰 무효·만료 또는 다른 태그의 토큰 (TOKEN_INVALID)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "존재하지 않는 태그 (TAG_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{tagCode}/ownership/me")
    public ResponseEntity<OwnerHomeResponse> getOwnerHome(
        @Parameter(description = "QR 코드 값", example = "A1B2-C3D4") @PathVariable String tagCode
    ) {
        return ResponseEntity.ok(tagService.getOwnerHome(tagCode));
    }
}
