package com.genai.gitgpt.user.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "gitgpt.gemini")
public class GeminiProperties {

    /**
     * Optional boot-only Gemini key ({@code GOOGLE_API_KEY}). Lets Spring AI start
     * without a user session. Index and ask always use the signed-in user's Settings key.
     */
    private String serverApiKey = "";

    private String defaultChatModel = "gemini-3.5-flash-lite";
    private String defaultEmbeddingModel = "gemini-embedding-001";
    private int embeddingDimensions = 1536;
}
