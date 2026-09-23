package com.genai.gitgpt.user.security;

import com.genai.gitgpt.user.config.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SessionTokenServiceTest {

    private SessionTokenService tokens;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties();
        properties.setTokenEncryptionKey(Base64.getEncoder()
                .encodeToString("0123456789abcdef0123456789abcdef".getBytes()));
        tokens = new SessionTokenService(properties);
    }

    @Test
    void roundTrip() {
        String token = tokens.issue("42");
        assertEquals("42", tokens.parseGithubId(token));
        assertNotEquals(token, tokens.issue("99"));
    }

    @Test
    void expiredAndTamperedAreRejected() {
        assertNull(tokens.parseGithubId(tokens.issue("42", Duration.ofMillis(-5))));
        String token = tokens.issue("42");
        assertNull(tokens.parseGithubId(token + "x"));
        assertNull(tokens.parseGithubId(null));
    }
}
