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
}
