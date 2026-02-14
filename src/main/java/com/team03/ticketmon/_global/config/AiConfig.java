package com.team03.ticketmon._global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.chat.client.ChatClient;

@Configuration

public class AiConfig {

    @Value("${spring.ai.summary.system-prompt}")
    private String systemPrompt;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem(systemPrompt)
                .build();
    }
}