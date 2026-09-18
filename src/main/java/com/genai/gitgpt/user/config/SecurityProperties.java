package com.genai.gitgpt.user.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "gitgpt.security")
public class SecurityProperties {

    /**
     * Base64-encoded 32-byte AES-256 key. Set via {@code GITGPT_TOKEN_ENCRYPTION_KEY}.
     */
    private String tokenEncryptionKey;

    private int tokenCacheTtlMinutes = 20;
    private int askRateLimit = 30;
    private int askRateWindowSeconds = 600;
    private int indexRateLimit = 6;
    private int indexRateWindowSeconds = 900;
}
