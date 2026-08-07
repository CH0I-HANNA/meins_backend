package com.mcm.onboarding.domain.chat.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;

@Component
public class LlmWebClient {

    private final WebClient llmApiWebClient;

    public LlmWebClient(@Qualifier("llmApiWebClient") WebClient llmApiWebClient) {
        this.llmApiWebClient = llmApiWebClient;
    }

    public Flux<String> streamCompletion(String systemPrompt, String userMessage) {
        // TODO: 실제 LLM API 연동 시 아래 더미 응답을 교체
        // 더미 스트리밍: 프론트엔드 SSE 연동 테스트용
        return Flux.just("안녕하세요! ", "MCM ", "제품에 ", "대해 ", "무엇이든 ", "물어보세요.")
            .delayElements(Duration.ofMillis(150));

        // 실제 OpenAI 스트리밍 연동 예시:
        // return llmWebClient.post()
        //     .uri("/chat/completions")
        //     .bodyValue(Map.of(
        //         "model", "gpt-4o",
        //         "stream", true,
        //         "messages", List.of(
        //             Map.of("role", "system", "content", systemPrompt),
        //             Map.of("role", "user", "content", userMessage)
        //         )
        //     ))
        //     .retrieve()
        //     .bodyToFlux(String.class)
        //     .filter(chunk -> !"[DONE]".equals(chunk));
    }
}