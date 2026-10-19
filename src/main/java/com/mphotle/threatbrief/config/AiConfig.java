package com.mphotle.threatbrief.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AiConfig {

    @Bean
    public ChatModel chatModel(
            @Value("${huggingface.api.token}") String token,
            @Value("${huggingface.model.id}") String modelId,
            @Value("${huggingface.max.tokens:3500}") int maxTokens,
            @Value("${huggingface.temperature:0.3}") double temperature) {
        return OpenAiChatModel.builder()
                .baseUrl("https://router.huggingface.co/v1")
                .apiKey(token)
                .modelName(modelId)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(60))
                .build();
    }
}
