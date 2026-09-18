package com.genai.gitgpt.user.security;

import com.genai.gitgpt.exception.RateLimitException;
import com.genai.gitgpt.user.config.SecurityProperties;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimitServiceTest {

    @Test
    void rejectsWhenTheWindowIsFull() {
        SecurityProperties properties = new SecurityProperties();
        properties.setAskRateLimit(2);
        properties.setAskRateWindowSeconds(60);
        RateLimitService limiter = new RateLimitService(properties);
        UUID userId = UUID.randomUUID();

        assertDoesNotThrow(() -> limiter.checkAsk(userId));
        assertDoesNotThrow(() -> limiter.checkAsk(userId));
        assertThrows(RateLimitException.class, () -> limiter.checkAsk(userId));
    }
}
