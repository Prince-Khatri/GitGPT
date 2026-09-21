package com.genai.gitgpt.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SecretRedactorTest {

    @Test
    void redactsGithubTokensAndCiphertext() {
        String redacted = SecretRedactor.redact(
                "Bearer ghp_abcdefghijklmnopqrstuvwxyz123456 and enc:v2:abcd+/== leftover"
        );
        assertFalse(redacted.contains("ghp_"));
        assertFalse(redacted.contains("enc:v2:"));
        assertEquals("[redacted] and [redacted] leftover", redacted);
        assertFalse(SecretRedactor.redact("enc:v1:abcd+/==").contains("enc:v1:"));
    }

    @Test
    void redactsGeminiApiKeys() {
        String redacted = SecretRedactor.redact("key=AIzaSyA-this-is-a-fake-gemini-key-value");
        assertFalse(redacted.contains("AIza"));
        assertEquals("key=[redacted]", redacted);
    }

    @Test
    void leavesOrdinaryErrorsAlone() {
        assertEquals("Failed to download snapshot (HTTP 404).",
                SecretRedactor.redact("Failed to download snapshot (HTTP 404)."));
    }
}
