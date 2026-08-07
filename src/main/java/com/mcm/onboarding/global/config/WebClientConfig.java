package com.mcm.onboarding.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${llm.api.base-url:https://api.openai.com/v1}")
    private String llmBaseUrl;

    @Value("${llm.api.key:}")
    private String llmApiKey;

    @Bean
    public WebClient llmApiWebClient() {
        return WebClient.builder()
            .baseUrl(llmBaseUrl)
            .defaultHeader("Authorization", "Bearer " + llmApiKey)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }
}
