package com.genai.gitgpt.user.config;

import com.google.genai.Client;
import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Keeps Spring AI chat/embedding auto-config able to start when no server
 * {@code GOOGLE_API_KEY} is set. Index and ask use the user's stored key instead.
 */
@Configuration
public class GeminiBootConfig {

    private static final String PLACEHOLDER = "missing-gemini-key";

    @Bean
    @ConditionalOnMissingBean(Client.class)
    Client googleGenAiClient(@Value("${spring.ai.google.genai.api-key:}") String apiKey) {
        return Client.builder().apiKey(resolve(apiKey)).vertexAI(false).build();
    }

    @Bean
    @ConditionalOnMissingBean(GoogleGenAiEmbeddingConnectionDetails.class)
    GoogleGenAiEmbeddingConnectionDetails googleGenAiEmbeddingConnectionDetails(
            @Value("${spring.ai.google.genai.embedding.api-key:${spring.ai.google.genai.api-key:}}") String apiKey
    ) {
        String resolved = resolve(apiKey);
        return GoogleGenAiEmbeddingConnectionDetails.builder()
                .apiKey(resolved)
                .genAiClient(Client.builder().apiKey(resolved).vertexAI(false).build())
                .build();
    }

    private static String resolve(String apiKey) {
        return StringUtils.hasText(apiKey) ? apiKey : PLACEHOLDER;
    }
}
