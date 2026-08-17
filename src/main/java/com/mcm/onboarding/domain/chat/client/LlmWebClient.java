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
            .map(this::extractDeltaText)
            .filter(content -> !content.isEmpty());
    }

    // AI 서버 스트리밍 청크 형식: {"type":"delta","text":"..."} / {"type":"done"}
    // done이나 알 수 없는 type은 텍스트가 없어 빈 문자열로 걸러진다.
    private String extractDeltaText(String chunkJson) {
        try {
            JsonNode node = objectMapper.readTree(chunkJson);
            if (!"delta".equals(node.path("type").asString(""))) {
                return "";
            }
            return node.path("text").asString("");
        } catch (Exception e) {
            return "";
        }
    }
}
