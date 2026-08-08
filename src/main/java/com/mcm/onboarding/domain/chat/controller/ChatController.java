package com.mcm.onboarding.domain.chat.controller;

import com.mcm.onboarding.common.dto.ErrorResponse;
import com.mcm.onboarding.domain.chat.dto.ChatHistoryResponse;
import com.mcm.onboarding.domain.chat.dto.ChatRequest;
import com.mcm.onboarding.domain.chat.service.ChatHarnessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Chat", description = "AI 챗 스트리밍 및 대화 히스토리")
@RestController
@RequestMapping("/api/tags/{tagCode}/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatHarnessService chatHarnessService;

    @Operation(
        summary = "AI 챗 SSE 스트리밍 (06)",
        description = """
            AI 챗봇과 실시간 스트리밍 대화를 시작합니다. Bearer 토큰 필수.

            **하네스 3계층 처리 순서**
            1. 크레딧 체크 → 0 이하면 즉시 `429` 반환 (LLM 미호출)
            2. DB에서 제품 정보 조회 → 가드레일 + 컨텍스트 시스템 프롬프트 조립
            3. LLM SSE 스트리밍 → 클라이언트 연결 끊김 시 서버 LLM 구독 즉시 취소

            **가드레일 (고정)**: 가품 판정 / 가격 산정 / 리셀 시세 언급 금지

            **프리셋 사용 예시**
            - `{ "preset": "care" }` — 관리법·보관법 안내
            - `{ "preset": "style" }` — 스타일링·코디 조언
            - `{ "preset": "heritage" }` — MCM 브랜드 헤리티지 안내
            - `{ "message": "..." }` — 자유 문의
            """,
        security = @SecurityRequirement(name = "OwnerToken")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "SSE 스트림 시작 (text/event-stream)"),
        @ApiResponse(responseCode = "401", description = "토큰 무효·만료 또는 다른 태그의 토큰 (TOKEN_INVALID)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "크레딧 소진 (CREDIT_EXHAUSTED)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
        @Parameter(description = "QR 코드 값", example = "A1B2-C3D4") @PathVariable String tagCode,
        @RequestBody ChatRequest request
    ) {
        return chatHarnessService.streamChat(tagCode, request);
    }

    @Operation(
        summary = "챗 히스토리 조회 (06 진입)",
        description = """
            해당 태그의 전체 대화 내역을 시간순으로 반환합니다. Bearer 토큰 필수.

            이력은 서버에 `tagCode` 기준으로 저장됩니다 — 기기를 바꿔도 복원됩니다.
            `credits.remaining`이 2 이하일 때 프론트가 안내 문구를 띄웁니다.
            """,
        security = @SecurityRequirement(name = "OwnerToken")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "히스토리 조회 성공"),
        @ApiResponse(responseCode = "401", description = "토큰 무효·만료 또는 다른 태그의 토큰 (TOKEN_INVALID)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/history")
    public ResponseEntity<ChatHistoryResponse> getChatHistory(
        @Parameter(description = "QR 코드 값", example = "A1B2-C3D4") @PathVariable String tagCode
    ) {
        return ResponseEntity.ok(chatHarnessService.getChatHistory(tagCode));
    }
}
