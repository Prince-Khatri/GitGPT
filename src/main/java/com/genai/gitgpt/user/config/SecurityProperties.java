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

    /**
     * Session + CSRF cookie SameSite. Use {@code none} when the UI and API are on
     * different hosts (requires {@code cookieSecure=true} / HTTPS).
     */
    private String cookieSameSite = "lax";

    private boolean cookieSecure = false;

    /**
     * Netlify + Render (or any remote HTTPS UI) must send the session cookie on
     * cross-site {@code fetch}. Browsers drop {@code SameSite=None} unless Secure.
     * Local {@code http://localhost} keeps Lax.
     */
    public String effectiveCookieSameSite(String frontendOrigin) {
        if (requiresCrossSiteCookies(frontendOrigin)) {
            return "none";
        }
        if (cookieSameSite == null || cookieSameSite.isBlank()) {
            return "lax";
        }
        return cookieSameSite;
    }

    public boolean effectiveCookieSecure(String frontendOrigin) {
        return requiresCrossSiteCookies(frontendOrigin) || cookieSecure;
    }

    static boolean requiresCrossSiteCookies(String frontendOrigin) {
        if (frontendOrigin == null || frontendOrigin.isBlank()) {
            return false;
        }
        String origin = frontendOrigin.trim().toLowerCase();
        if (!origin.startsWith("https://")) {
            return false;
        }
        return !origin.contains("localhost") && !origin.contains("127.0.0.1");
    }
}
