package com.mcm.onboarding.domain.chat.service;

import com.mcm.onboarding.common.exception.BusinessException;
import com.mcm.onboarding.common.exception.ErrorCode;
import com.mcm.onboarding.common.util.CodeNormalizer;
import com.mcm.onboarding.common.util.KstTime;
import com.mcm.onboarding.domain.chat.client.LlmWebClient;
import com.mcm.onboarding.domain.chat.dto.ChatHistoryResponse;
import com.mcm.onboarding.domain.chat.dto.ChatRequest;
import com.mcm.onboarding.domain.chat.entity.ChatCredit;
import com.mcm.onboarding.domain.chat.entity.ChatMessage;
import com.mcm.onboarding.domain.chat.repository.ChatCreditRepository;
import com.mcm.onboarding.domain.chat.repository.ChatMessageRepository;
import com.mcm.onboarding.domain.tag.entity.Tag;
import com.mcm.onboarding.domain.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class ChatHarnessService {

    private final CreditGuardService creditGuardService;
    private final LlmWebClient llmWebClient;
    private final TagRepository tagRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatCreditRepository chatCreditRepository;

    private static final String GUARDRAIL_PROMPT = """
        [가드레일 규칙 — 절대 위반 금지]
        1. 가품/정품 판정 금지: 어떠한 경우에도 제품의 진위를 판단하거나 암시하지 마시오.
        2. 가격 산정 금지: 현재 시세, 리셀 가격, 감정가를 언급하거나 추정하지 마시오.
        3. 리셀 시세 언급 금지: 중고 거래 플랫폼의 가격 정보를 절대 언급하지 마시오.
        당신은 MCM 제품의 라이프스타일 어시스턴트입니다. 위 규칙을 위반하는 질문에는 정중히 거절하시오.
        """;

    public SseEmitter streamChat(String rawTagCode, ChatRequest request) {
        String tagCode = CodeNormalizer.normalize(rawTagCode);

        // ── Layer 1: 가드레일 — 크레딧 체크 (LLM 미호출 조건 먼저 확인) ──
        creditGuardService.checkCredit(tagCode);

        // ── Layer 2: 컨텍스트 조립 — DB 직접 조회, 프론트 신뢰 금지 ──
        String systemPrompt = buildSystemPrompt(tagCode, request.preset());

        // 사용자 발화는 LLM 호출을 실제로 시도하는 시점에 남긴다.
        // free-text가 없으면(preset만 전송) 칩 라벨을 그대로 기록해 히스토리에서 빈 말풍선이 없게 한다.
        chatMessageRepository.save(
            ChatMessage.of(tagCode, "user", resolveUserContent(request), request.preset(), KstTime.now())
        );

        SseEmitter emitter = new SseEmitter(60_000L);
        StringBuilder assistantContent = new StringBuilder();
        // 명세 2-5 요청사항 3: 차감은 "스트림 종료 시(중단 포함)"에만 하고 "호출 실패 시엔 미차감".
        // 중단 시 이후 청크들이 연달아 emitter.send() 실패로 catch에 걸릴 수 있어, 1턴에 1번만
        // 차감되도록 가드가 필요하다.
        AtomicBoolean creditSettled = new AtomicBoolean(false);

        // ── Layer 3: LLM 스트리밍 실행 ──
        Disposable subscription = llmWebClient.streamCompletion(systemPrompt, request.message())
            .subscribe(
                chunk -> {
                    assistantContent.append(chunk);
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (IOException e) {
                        // 클라이언트 중단 — 명세상 중단이어도 1턴 차감 대상
                        if (creditSettled.compareAndSet(false, true)) {
                            creditGuardService.deductCredit(tagCode);
                        }
                        emitter.completeWithError(e);
                    }
                },
                error -> {
                    // 호출 실패(LLM 자체 에러) — 명세상 미차감. 완료/중단과 달리 크레딧을 건드리지 않는다.
                    emitter.completeWithError(error);
                },
                () -> {
                    // 정상 종료 — 차감 + 저장
                    if (creditSettled.compareAndSet(false, true)) {
                        creditGuardService.deductCredit(tagCode);
                    }
                    chatMessageRepository.save(
                        ChatMessage.of(tagCode, "assistant", assistantContent.toString(), request.preset(), KstTime.now())
                    );
                    emitter.complete();
                }
            );

        // Abort 대응: 클라이언트 연결 끊김 시 LLM WebClient 구독 즉시 취소
        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(subscription::dispose);
        emitter.onError(e -> subscription.dispose());

        return emitter;
    }

    public ChatHistoryResponse getChatHistory(String rawTagCode) {
        String tagCode = CodeNormalizer.normalize(rawTagCode);
        List<ChatMessage> messages = chatMessageRepository.findByTagCodeOrderByCreatedAtAsc(tagCode);
        int remaining = chatCreditRepository.findRemainingByTagCode(tagCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.TAG_NOT_FOUND));
        return ChatHistoryResponse.of(messages, remaining, ChatCredit.DEFAULT_LIMIT);
    }

    // message가 없는 프리셋 전용 요청(칩 클릭)의 user 메시지 content — ChatMessage.content는 NOT NULL이라
    // 무언가는 채워야 하고, 06 화면의 칩 라벨과 맞춰 히스토리에 그대로 노출한다.
    private String resolveUserContent(ChatRequest request) {
        if (request.message() != null && !request.message().isBlank()) {
            return request.message();
        }
        return switch (request.preset() != null ? request.preset() : "") {
            case "care"     -> "케어";
            case "style"    -> "스타일";
            case "heritage" -> "헤리티지";
            default         -> "(빈 메시지)";
        };
    }

    private String buildSystemPrompt(String tagCode, String preset) {
        Tag tag = tagRepository.findByTagCode(tagCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.TAG_NOT_FOUND));
        var product = tag.getProduct();

        String dimensions = (product.getWidthCm() != null && product.getDepthCm() != null && product.getHeightCm() != null)
            ? "%d x %d x %d cm".formatted(product.getWidthCm(), product.getDepthCm(), product.getHeightCm())
            : "정보 없음";

        String productContext = """
            [제품 컨텍스트 — 서버 주입]
            - 제품명: %s
            - 소재: %s
            - 색상: %s
            - 사이즈(가로x세로x높이): %s
            """.formatted(product.getProductName(), product.getMaterial(), product.getColor(), dimensions);

        String presetContext = switch (preset != null ? preset : "") {
            case "care"      -> "- 사용자 요청 유형: 가방 관리법 및 보관법 안내 [care]";
            case "style"     -> "- 사용자 요청 유형: 스타일링 및 코디 조언 [style]";
            case "heritage"  -> "- 사용자 요청 유형: MCM 브랜드 헤리티지 및 제품 역사 안내 [heritage]";
            default          -> "- 사용자 요청 유형: 일반 문의";
        };

        return GUARDRAIL_PROMPT + "\n" + productContext + "\n" + presetContext;
    }
}