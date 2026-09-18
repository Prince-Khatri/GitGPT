package com.genai.gitgpt.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SecretRedactorTest {

    @Test
    void redactsGithubTokensAndCiphertext() {
        String redacted = SecretRedactor.redact(
                "Bearer ghp_abcdefghijklmnopqrstuvwxyz123456 and enc:v1:abcd+/== leftover"
        );
        assertFalse(redacted.contains("ghp_"));
        assertFalse(redacted.contains("enc:v1:"));
        assertEquals("[redacted] and [redacted] leftover", redacted);
    }

    @Test
    void leavesOrdinaryErrorsAlone() {
        assertEquals("Failed to download snapshot (HTTP 404).",
                SecretRedactor.redact("Failed to download snapshot (HTTP 404)."));
    }
}
