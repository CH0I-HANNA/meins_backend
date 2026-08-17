package com.mcm.onboarding.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${ai.chat.base-url}")
    private String aiChatBaseUrl;

    @Bean
    public WebClient aiChatWebClient() {
        return WebClient.builder()
            .baseUrl(aiChatBaseUrl)
            .defaultHeader("Content-Type", "application/json")
            // ngrok 무료 터널은 프로그램에서 보내는 요청에도 경고 인터스티셜 HTML을 끼워 넣으므로 우회 헤더 필요.
            .defaultHeader("ngrok-skip-browser-warning", "true")
            .build();
    }
}
