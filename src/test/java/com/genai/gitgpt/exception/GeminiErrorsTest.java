package com.genai.gitgpt.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiErrorsTest {

    @Test
    void wrapsQuotaAsRateLimit() {
        AppException wrapped = GeminiErrors.wrap(new RuntimeException("RESOURCE_EXHAUSTED: 429 quota"));
        assertInstanceOf(RateLimitException.class, wrapped);
        assertTrue(GeminiErrors.isRateLimit(wrapped));
    }

    @Test
    void leavesOrdinaryFailuresAsAppErrors() {
        AppException wrapped = GeminiErrors.wrap(new RuntimeException("model not found"));
        assertFalse(wrapped instanceof RateLimitException);
        assertTrue(wrapped.getMessage().contains("not available"));
    }

    @Test
    void mapsUnsupportedAccessTokenToUserKeyMessage() {
        AppException wrapped = GeminiErrors.wrapEmbed(new RuntimeException(
                "401 ACCESS_TOKEN_TYPE_UNSUPPORTED invalid authentication credentials"
        ));
        assertTrue(GeminiErrors.isAuthFailure(wrapped.getCause()));
        assertTrue(wrapped.getMessage().contains("Settings"));
        assertFalse(wrapped.getMessage().contains("ACCESS_TOKEN"));
    }
}
