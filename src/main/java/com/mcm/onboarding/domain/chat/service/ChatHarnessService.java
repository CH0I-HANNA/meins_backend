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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class ChatHarnessService {

    private static final Logger log = LoggerFactory.getLogger(ChatHarnessService.class);

    private final CreditGuardService creditGuardService;
    private final LlmWebClient llmWebClient;
    private final TagRepository tagRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatCreditRepository chatCreditRepository;

    public SseEmitter streamChat(String rawTagCode, ChatRequest request) {
        String tagCode = CodeNormalizer.normalize(rawTagCode);

        // 명세상 message 또는 preset 중 하나는 반드시 있어야 한다. 검증 없이 통과시키면 빈 바디도
        // 크레딧을 소모하고 "(빈 메시지)"로 LLM을 호출하게 된다 — LLM 호출/차감 전에 걸러낸다.
        boolean noMessage = request.message() == null || request.message().isBlank();
        boolean noPreset = request.preset() == null || request.preset().isBlank();
        if (noMessage && noPreset) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        // ── Layer 1: 가드레일 — 크레딧 체크 + 원자적 선차감 (LLM 미호출 조건 먼저 확인) ──
        creditGuardService.checkCredit(tagCode);
        creditGuardService.reserveCredit(tagCode);

        // ── Layer 2: 컨텍스트 조립 — DB 직접 조회, 프론트 신뢰 금지 ──
        // AI 서버는 modelCode로 자체 RAG DB에서 제품 정보를 찾으므로, tagCode → modelCode 매핑만
        // 서버가 직접 조회해서 넘긴다(프론트가 modelCode를 임의로 지정하지 못하게).
        String modelCode = resolveModelCode(tagCode);

        // 사용자 발화는 LLM 호출을 실제로 시도하는 시점에 남긴다.
        // free-text가 없으면(preset만 전송) 칩 라벨을 그대로 기록해 히스토리에서 빈 말풍선이 없게 한다.
        chatMessageRepository.save(
            ChatMessage.of(tagCode, "user", resolveUserContent(request), request.preset(), KstTime.now())
        );

        // AI(RAG) 서버 응답이 60초를 넘는 질문이 실제로 있어, 60초로는 onComplete(히스토리 저장)까지
        // 못 가고 AsyncRequestTimeoutException으로 끊기는 사례가 확인됨 — 클라이언트엔 이미 전송된
        // 청크가 남아 "답변은 나왔는데 히스토리엔 없음"으로 보였다. spring.mvc.async.request-timeout과
        // 맞춰 180초로 상향.
        SseEmitter emitter = new SseEmitter(180_000L);
        StringBuilder assistantContent = new StringBuilder();
        // subscribe()가 반환하는 Disposable을 subscribe() 내부(onNext)에서도 즉시 취소하려면
        // 대입 완료 전에 참조해야 하는 순환이 생긴다 — 참조를 담을 그릇을 미리 만들어 우회한다.
        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();

        // ── Layer 3: LLM 스트리밍 실행 ──
        // preset 전용 요청(칩 클릭)은 message가 null이다 — 그대로 넘기면 요청 바디 조립 시점에
        // NPE가 나므로, 히스토리에 남긴 것과 같은 값(칩 라벨)을 LLM에도 보낸다.
        Disposable subscription = llmWebClient.streamCompletion(modelCode, resolveUserContent(request))
            .subscribe(
                chunk -> {
                    assistantContent.append(chunk);
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (Exception e) {
                        // 클라이언트가 스트림 중간에 연결을 끊은 경우(주로 IOException이지만, emitter가
                        // 이미 완료/타임아웃된 상태에서 send()를 호출하면 IllegalStateException도 난다 —
                        // 둘 다 여기서 잡아야 원인을 놓치지 않는다). completeWithError만으로는 업스트림
                        // LLM 구독이 즉시 끊기지 않으므로(onCompletion 콜백을 기다려야 함) 여기서 바로
                        // dispose해 CLAUDE.md의 "구독 즉시 취소" 요구를 충족한다.
                        log.warn("chat SSE send 실패, tagCode={}, 구독 dispose", tagCode, e);
                        emitter.completeWithError(e);
                        Disposable current = subscriptionRef.get();
                        if (current != null) {
                            current.dispose();
                        }
                    }
                },
                error -> {
                    // 명세 2-5 요청사항 3: "호출 실패 시 미차감". 레이스를 닫으려고 선차감해 둔 1턴을
                    // LLM 호출이 실패한 이 경로에서만 되돌린다. 정상 종료/클라이언트 중단은 차감 유지.
                    log.warn("chat 스트림 error, tagCode={}, 크레딧 환불", tagCode, error);
                    creditGuardService.refundCredit(tagCode);
                    emitter.completeWithError(error);
                },
                () -> {
                    log.info("chat 스트림 onComplete, tagCode={}, 응답 길이={}", tagCode, assistantContent.length());
                    try {
                        chatMessageRepository.save(
                            ChatMessage.of(tagCode, "assistant", assistantContent.toString(), request.preset(), KstTime.now())
                        );
                        log.info("chat assistant 메시지 저장 완료, tagCode={}", tagCode);
                    } catch (Exception e) {
                        log.error("chat assistant 메시지 저장 실패, tagCode={}", tagCode, e);
                        throw e;
                    } finally {
                        emitter.complete();
                    }
                }
            );
        subscriptionRef.set(subscription);

        // Abort 대응: 클라이언트 연결 끊김 시 LLM WebClient 구독 즉시 취소
        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(() -> {
            log.warn("chat SseEmitter 타임아웃, tagCode={}", tagCode);
            subscription.dispose();
        });
        emitter.onError(e -> {
            log.warn("chat SseEmitter onError, tagCode={}", tagCode, e);
            subscription.dispose();
        });

        return emitter;
    }

    public ChatHistoryResponse getChatHistory(String rawTagCode) {
        String tagCode = CodeNormalizer.normalize(rawTagCode);
        List<ChatMessage> messages = chatMessageRepository.findByTagCodeOrderByCreatedAtAsc(tagCode);
        ChatCredit credit = chatCreditRepository.findByTagCode(tagCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.TAG_NOT_FOUND));
        return ChatHistoryResponse.of(messages, credit.getRemaining(), credit.getLimit());
    }

    // message가 없는 프리셋 전용 요청(칩 클릭)의 user 메시지 content — ChatMessage.content는 NOT NULL이라
    // 무언가는 채워야 하고, 06 화면의 칩 라벨과 맞춰 히스토리에 그대로 노출한다.
    private String resolveUserContent(ChatRequest request) {
        if (request.message() != null && !request.message().isBlank()) {
            return request.message();
        }
        String label = chipLabel(request.preset());
        return label != null ? label : "(빈 메시지)";
    }

    private String chipLabel(String preset) {
        return switch (preset != null ? preset : "") {
            case "care"     -> "케어";
            case "style"    -> "스타일";
            case "heritage" -> "헤리티지";
            default         -> null;
        };
    }

    // AI 서버가 modelCode로 자체 RAG DB를 조회하므로, tagCode → modelCode 매핑은 프론트를 신뢰하지 않고
    // 서버가 직접 DB에서 찾아 채운다.
    private String resolveModelCode(String tagCode) {
        Tag tag = tagRepository.findByTagCode(tagCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.TAG_NOT_FOUND));
        String modelCode = tag.getProduct().getModelCode();
        if (modelCode == null || modelCode.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return modelCode;
    }
}