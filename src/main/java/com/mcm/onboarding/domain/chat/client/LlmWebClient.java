package com.mcm.onboarding.domain.chat.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Component
public class LlmWebClient {

    private final WebClient llmApiWebClient;
    private final ObjectMapper objectMapper;

    @Value("${llm.api.model:gpt-4o-mini}")
    private String model;

    public LlmWebClient(@Qualifier("llmApiWebClient") WebClient llmApiWebClient, ObjectMapper objectMapper) {
        this.llmApiWebClient = llmApiWebClient;
        this.objectMapper = objectMapper;
    }

    public Flux<String> streamCompletion(String systemPrompt, String userMessage) {
        Map<String, Object> requestBody = Map.of(
            "model", model,
            "stream", true,
            "temperature", 0.7,
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
            )
        );

        return llmApiWebClient.post()
            .uri("/chat/completions")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
            .mapNotNull(ServerSentEvent::data)
            .filter(data -> !"[DONE]".equals(data))
            .map(this::extractDeltaContent)
            .filter(content -> !content.isEmpty());
    }

    // OpenAI 스트리밍 청크 형식: {"choices":[{"delta":{"content":"..."}}]}
    // 첫 청크(role만 있음)·종료 청크(finish_reason만 있음)는 content가 없어 빈 문자열로 걸러진다.
    private String extractDeltaContent(String chunkJson) {
        try {
            JsonNode content = objectMapper.readTree(chunkJson)
                .path("choices").path(0).path("delta").path("content");
            return content.isMissingNode() ? "" : content.asText("");
        } catch (Exception e) {
            return "";
        }
    }
}