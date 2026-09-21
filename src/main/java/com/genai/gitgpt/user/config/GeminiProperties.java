package com.genai.gitgpt.user.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "gitgpt.gemini")
public class GeminiProperties {

    /**
     * Optional server-wide Gemini key ({@code GOOGLE_API_KEY}). Used only when the user
     * has not stored their own key.
     */
    private String serverApiKey = "";

    private String defaultChatModel = "gemini-3.5-flash-lite";
    private String defaultEmbeddingModel = "gemini-embedding-001";
    private int embeddingDimensions = 1536;
}
