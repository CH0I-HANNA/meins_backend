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
import com.mcm.onboarding.domain.ownership.entity.OwnershipRecord;
import com.mcm.onboarding.domain.ownership.repository.OwnershipRepository;
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

    // SSE 종료 신호. 데이터 청크는 "data: {내용}"으로만 나가므로, 이름 있는 이벤트를 쓰면
    // 기존 클라이언트(= data: 줄만 읽는 파서)와 충돌하지 않는다 — 자세한 배경은 sendTerminalEvent().
    private static final String DONE_EVENT = "done";
    private static final String ERROR_EVENT = "error";

    private final CreditGuardService creditGuardService;
    private final LlmWebClient llmWebClient;
    private final TagRepository tagRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatCreditRepository chatCreditRepository;
    private final OwnershipRepository ownershipRepository;

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
        // 스트리밍이 진행되는 동안 소유권이 이전될 수 있다. 이전은 히스토리를 비우고 ownerSecret을
        // 회전시키므로, 시작 시점의 ownerSecret을 들고 있다가 저장 직전에 대조한다(아래 onComplete).
        String ownerSecretAtStart = currentOwnerSecret(tagCode);

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
                        // Spring의 SseEmitter는 "data:" 뒤에 공백을 붙여주지 않고 값을 그대로 이어붙인다
                        // (data:Tr). 그런데 프론트(다른 SSE 클라이언트 일반)는 관례적으로 "data: " 뒤에
                        // 공백이 있다고 가정하고 파싱한다 — 공백 유무가 청크 값 자체(우연히 공백으로
                        // 시작하는 경우)에 따라 들쭉날쭉해지면 그 가정에 맞는 줄만 살아남고 나머지는
                        // 통째로 유실된다. 공백을 여기서 항상 명시적으로 붙여 "data: {원본 청크}" 형태를
                        // 보장한다 — 프론트는 "data: " 6글자만 잘라내면 원본 청크를 그대로 복원할 수 있다
                        // (trim()은 하면 안 된다 — 청크 자체의 앞뒤 공백이 실제 단어 사이 띄어쓰기다).
                        emitter.send(SseEmitter.event().data(" " + chunk));
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
                    // 스트림이 이미 시작된 뒤라 표준 에러 바디({code,message,traceId})로는 못 내려간다.
                    // 대신 error 이벤트를 한 번 실어 보내 프론트가 "중간에 끊긴 응답"임을 알 수 있게 한다.
                    // 전송 자체가 실패하면(클라이언트가 이미 끊었거나 emitter가 닫힌 경우) 기존처럼 끊는다.
                    if (sendTerminalEvent(emitter, ERROR_EVENT, tagCode)) {
                        emitter.complete();
                    } else {
                        emitter.completeWithError(error);
                    }
                },
                () -> {
                    log.info("chat 스트림 onComplete, tagCode={}, 응답 길이={}", tagCode, assistantContent.length());
                    // 스트림 도중 소유권이 이전됐다면 이 답변은 이전 소유자의 대화다. 그대로 저장하면
                    // 이전 시점에 비워진 히스토리에 assistant 메시지만 홀로 남아(질문은 이미 삭제됨)
                    // 새 소유자 화면에 남의 대화가 보인다 — 저장을 건너뛴다.
                    if (!isSameOwner(tagCode, ownerSecretAtStart)) {
                        log.warn("chat 스트림 완료 시점에 소유자가 바뀜 — assistant 메시지 저장 생략, tagCode={}", tagCode);
                        sendTerminalEvent(emitter, DONE_EVENT, tagCode);
                        emitter.complete();
                        return;
                    }
                    try {
                        chatMessageRepository.save(
                            ChatMessage.of(tagCode, "assistant", assistantContent.toString(), request.preset(), KstTime.now())
                        );
                        log.info("chat assistant 메시지 저장 완료, tagCode={}", tagCode);
                    } catch (Exception e) {
                        log.error("chat assistant 메시지 저장 실패, tagCode={}", tagCode, e);
                        throw e;
                    } finally {
                        // 답변을 끝까지 보냈다는 신호. 이 이벤트 없이 끊긴 스트림은 프론트가 실패로 간주한다.
                        sendTerminalEvent(emitter, DONE_EVENT, tagCode);
                        emitter.complete();
                    }
                }
            );
        subscriptionRef.set(subscription);

        // Abort 대응: 클라이언트 연결 끊김 시 LLM WebClient 구독 즉시 취소
        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(() -> {
            // 180초 타임아웃도 프론트 입장에선 "중간에 끊긴 응답"이다 — 컨테이너가 응답을 닫기 전에
            // best-effort로 error를 실어 보낸다(이미 닫혔으면 sendTerminalEvent가 조용히 false).
            log.warn("chat SseEmitter 타임아웃, tagCode={}", tagCode);
            sendTerminalEvent(emitter, ERROR_EVENT, tagCode);
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

    // 스트림의 마지막에 이름 있는 이벤트를 한 번 실어 보낸다("event: done" / "event: error").
    //
    // data는 비워 보낸다. Spring은 빈 data를 "data:"(뒤에 공백 없음)로 쓰는데, 청크는 항상
    // "data: {내용}"(공백 포함)으로 나가므로 기존 프론트 파서가 startsWith("data: ")로 거르면
    // 이 줄은 자연히 무시되고, "data:"까지만 보고 자르는 파서라도 빈 문자열이 붙을 뿐이라
    // 말풍선이 오염되지 않는다. 즉 프론트가 대응하기 전에 배포해도 안전하다.
    //
    // 전송 성공 여부를 돌려준다 — 실패는 클라이언트가 이미 연결을 끊었다는 뜻이라 호출부가
    // 그에 맞게(기존처럼 끊기) 처리한다.
    private boolean sendTerminalEvent(SseEmitter emitter, String eventName, String tagCode) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(""));
            log.info("chat 종료 이벤트 전송: {}, tagCode={}", eventName, tagCode);
            return true;
        } catch (Exception e) {
            log.warn("chat 종료 이벤트({}) 전송 실패 — 클라이언트가 이미 끊었을 수 있음, tagCode={}", eventName, tagCode, e);
            return false;
        }
    }

    // 스트림 시작 시점의 소유자 식별값. 레코드가 없으면(이론상 불가) null을 반환하고, 그 경우
    // isSameOwner()가 false가 되어 저장을 건너뛰는 안전한 쪽으로 기운다.
    private String currentOwnerSecret(String tagCode) {
        return ownershipRepository.findByTag_TagCode(tagCode)
            .map(OwnershipRecord::getOwnerSecret)
            .orElse(null);
    }

    private boolean isSameOwner(String tagCode, String ownerSecretAtStart) {
        String now = currentOwnerSecret(tagCode);
        return ownerSecretAtStart != null && ownerSecretAtStart.equals(now);
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