package com.mcm.onboarding.domain.admin.controller;

import com.mcm.onboarding.common.dto.ErrorResponse;
import com.mcm.onboarding.domain.admin.dto.AdminTagResponse;
import com.mcm.onboarding.domain.admin.dto.BulkCreateRequest;
import com.mcm.onboarding.domain.admin.dto.BulkCreateResponse;
import com.mcm.onboarding.domain.admin.dto.ForceStatusRequest;
import com.mcm.onboarding.domain.admin.service.AdminTagService;
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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin", description = "관리자 제품/태그(QR) 발급 및 운영")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "AdminKey")
public class AdminTagController {

    private final AdminTagService adminTagService;

    @Operation(summary = "제품 등록 + 태그(QR) 일괄 생성", description = "제품 정보를 입력받아 productId 1건과 tagCode/authCode N건을 일괄 생성합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "생성 성공"),
        @ApiResponse(responseCode = "401", description = "관리자 키 없음/불일치 (ADMIN_KEY_INVALID)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/tags/bulk-create")
    public ResponseEntity<BulkCreateResponse> bulkCreate(@Valid @RequestBody BulkCreateRequest request) {
        return ResponseEntity.ok(adminTagService.bulkCreate(request));
    }

    @Operation(summary = "태그 목록 조회", description = "생성된 모든 태그와 상태, QR 이미지 URL을 반환합니다.")
    @GetMapping("/tags")
    public ResponseEntity<List<AdminTagResponse>> listTags() {
        return ResponseEntity.ok(adminTagService.listTags());
    }

    @Operation(summary = "QR 이미지 단건 조회", description = "tagCode를 인코딩한 QR PNG 이미지를 즉석 생성해 반환합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "PNG 이미지"),
        @ApiResponse(responseCode = "404", description = "존재하지 않는 태그 (TAG_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping(value = "/qr/{tagCode}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getQrImage(
        @Parameter(description = "QR 코드 값", example = "AB3D-9F2K") @PathVariable String tagCode
    ) {
        return ResponseEntity.ok(adminTagService.generateQrPng(tagCode));
    }

    @Operation(summary = "QR 이미지 일괄 다운로드", description = "생성된 모든 태그의 QR PNG를 zip으로 묶어 다운로드합니다.")
    @GetMapping(value = "/tags/qr-export", produces = "application/zip")
    public ResponseEntity<byte[]> exportQrZip() {
        byte[] zip = adminTagService.generateQrZip();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename("mcm-qr-codes.zip").build().toString())
            .body(zip);
    }

    @Operation(
        summary = "태그 상태 강제 변경",
        description = """
            잠금 해제(운영) 또는 등록 상태 강제 세팅(데모 준비)에 사용합니다.
            - UNLOCK: 잠금/실패횟수만 초기화
            - UNLOCK_RECOVERY: 잠금 해제 + 등록 이력 초기화
            - REGISTERED / UNREGISTERED: 데모용 상태 강제 세팅
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "변경 성공"),
        @ApiResponse(responseCode = "404", description = "존재하지 않는 태그 (TAG_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/tags/{tagCode}/force-status")
    public ResponseEntity<Void> forceStatus(
        @Parameter(description = "QR 코드 값", example = "AB3D-9F2K") @PathVariable String tagCode,
        @Valid @RequestBody ForceStatusRequest request
    ) {
        adminTagService.forceStatus(tagCode, request.action());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "태그 개별 삭제", description = "태그와 연관된 소유권 기록/시도 이력/채팅 크레딧/채팅 메시지를 함께 삭제합니다. 태그가 속한 제품(product)은 삭제하지 않습니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "삭제 성공"),
        @ApiResponse(responseCode = "404", description = "존재하지 않는 태그 (TAG_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/tags/{tagCode}")
    public ResponseEntity<Void> deleteTag(
        @Parameter(description = "QR 코드 값", example = "AB3D-9F2K") @PathVariable String tagCode
    ) {
        adminTagService.deleteTag(tagCode);
        return ResponseEntity.noContent().build();
    }
}
