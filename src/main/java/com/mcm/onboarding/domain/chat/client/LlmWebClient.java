package com.mcm.onboarding.domain.chat.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Component
public class LlmWebClient {

    private final WebClient aiChatWebClient;
    private final ObjectMapper objectMapper;

    public LlmWebClient(@Qualifier("aiChatWebClient") WebClient aiChatWebClient, ObjectMapper objectMapper) {
        this.aiChatWebClient = aiChatWebClient;
        this.objectMapper = objectMapper;
    }

    // AI 담당자 RAG 서버 계약: POST /chat/stream, body {modelCode, message} → modelCode는
    // 프론트가 아니라 우리 백엔드가 tagCode로 DB 조회해서 채워야 한다(컨텍스트 위조 방지 원칙).
    // 가드레일(가품 판정/가격/리셀 시세 금지)은 AI 서버가 자체 RAG+프롬프트로 처리하므로
    // 여기서 별도 시스템 프롬프트를 실어 보내지 않는다.
    public Flux<String> streamCompletion(String modelCode, String userMessage) {
        Map<String, Object> requestBody = Map.of(
            "modelCode", modelCode,
            "message", userMessage
        );

        return aiChatWebClient.post()
            .uri("/chat/stream")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
            .mapNotNull(ServerSentEvent::data)
            .map(this::parseChunk)
            // AI 서버가 {"type":"done"}은 보내지만 그 뒤 HTTP 연결을 제대로 안 닫는 경우가 실제로
            // 있어(질문에 따라 다름), 연결 종료를 기다리면 우리 쪽 Flux가 영영 onComplete되지 않고
            // ChatHarnessService의 저장 로직이 통째로 실행 안 되는 문제가 있었다. done 청크 자체를
            // 완료 신호로 취급해 연결 상태와 무관하게 우리가 스스로 스트림을 끝낸다.
            .takeUntil(Chunk::done)
            .map(Chunk::text)
            .filter(text -> !text.isEmpty());
    }

    // AI 서버 스트리밍 청크 형식: {"type":"delta","text":"..."} / {"type":"done"}
    private Chunk parseChunk(String chunkJson) {
        try {
            JsonNode node = objectMapper.readTree(chunkJson);
            String type = node.path("type").asString("");
            if ("done".equals(type)) {
                return new Chunk("", true);
            }
            if (!"delta".equals(type)) {
                return new Chunk("", false);
            }
            return new Chunk(node.path("text").asString(""), false);
        } catch (Exception e) {
            return new Chunk("", false);
        }
    }

    private record Chunk(String text, boolean done) {}
}
