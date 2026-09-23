package com.genai.gitgpt.user.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityPropertiesTest {

    @Test
    void remoteHttpsUiForcesNoneAndSecure() {
        SecurityProperties properties = new SecurityProperties();
        properties.setCookieSameSite("lax");
        properties.setCookieSecure(false);

        String origin = "https://gitgpt-ai.netlify.app";
        assertEquals("none", properties.effectiveCookieSameSite(origin));
        assertTrue(properties.effectiveCookieSecure(origin));
        assertTrue(SecurityProperties.requiresCrossSiteCookies(origin + "/"));
    }

    @Test
    void localhostKeepsConfiguredLax() {
        SecurityProperties properties = new SecurityProperties();
        properties.setCookieSameSite("lax");
        properties.setCookieSecure(false);

        assertEquals("lax", properties.effectiveCookieSameSite("http://localhost:5173"));
        assertFalse(properties.effectiveCookieSecure("http://localhost:5173"));
        assertFalse(SecurityProperties.requiresCrossSiteCookies("http://localhost:5173"));
    }
}
