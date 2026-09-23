package com.genai.gitgpt.user.security;

import com.genai.gitgpt.user.config.SecurityProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * HMAC session token for the SPA. Holds only the GitHub id and expiry — never
 * the GitHub access token or Gemini key.
 */
@Service
public class SessionTokenService {

    private static final Duration DEFAULT_TTL = Duration.ofHours(12);
    private static final String HMAC = "HmacSHA256";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final SecurityProperties properties;

    public SessionTokenService(SecurityProperties properties) {
        this.properties = properties;
    }

    public String issue(String githubId) {
        return issue(githubId, DEFAULT_TTL);
    }

    String issue(String githubId, Duration ttl) {
        if (!StringUtils.hasText(githubId)) {
            throw new IllegalArgumentException("githubId is required");
        }
        long exp = System.currentTimeMillis() + ttl.toMillis();
        String payload = B64.encodeToString((githubId.trim() + "\n" + exp).getBytes(StandardCharsets.UTF_8));
        return "v1." + payload + "." + sign(payload);
    }

    public String parseGithubId(String token) {
        if (!StringUtils.hasText(token) || !token.startsWith("v1.")) {
            return null;
        }
        String[] parts = token.split("\\.", 3);
        if (parts.length != 3 || !"v1".equals(parts[0]) || !StringUtils.hasText(parts[1]) || !StringUtils.hasText(parts[2])) {
            return null;
        }
        if (!sign(parts[1]).equals(parts[2])) {
            return null;
        }
        try {
            String decoded = new String(B64D.decode(parts[1]), StandardCharsets.UTF_8);
            int split = decoded.lastIndexOf('\n');
            if (split <= 0) {
                return null;
            }
            long exp = Long.parseLong(decoded.substring(split + 1));
            if (exp < System.currentTimeMillis()) {
                return null;
            }
            String githubId = decoded.substring(0, split).trim();
            return StringUtils.hasText(githubId) ? githubId : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public long ttlSeconds() {
        return DEFAULT_TTL.toSeconds();
    }

    private String sign(String payload) {
        try {
            byte[] key = TokenCipher.decodeKey(properties.getTokenEncryptionKey());
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            mac.update("session-token".getBytes(StandardCharsets.UTF_8));
            return B64.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign the session token.");
        }
    }
}
