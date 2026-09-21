package com.genai.gitgpt.user.gemini;

import com.genai.gitgpt.exception.AppException;
import com.genai.gitgpt.user.config.GeminiProperties;
import com.genai.gitgpt.user.config.SecurityProperties;
import com.genai.gitgpt.user.models.Users;
import com.genai.gitgpt.user.repository.UserRepository;
import com.genai.gitgpt.user.security.TokenCipher;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

@Service
public class GeminiKeyService {

    private final TokenCipher tokenCipher;
    private final UserRepository userRepository;
    private final GeminiProperties geminiProperties;
    private final Cache<UUID, String> decryptedKeys;

    public GeminiKeyService(
            TokenCipher tokenCipher,
            UserRepository userRepository,
            GeminiProperties geminiProperties,
            SecurityProperties securityProperties
    ) {
        this.tokenCipher = tokenCipher;
        this.userRepository = userRepository;
        this.geminiProperties = geminiProperties;
        this.decryptedKeys = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(Math.max(1, securityProperties.getTokenCacheTtlMinutes())))
                .maximumSize(10_000)
                .build();
    }

    public boolean hasUserKey(Users user) {
        return user != null && StringUtils.hasText(user.getGeminiApiKey());
    }

    public boolean hasServerKey() {
        return StringUtils.hasText(geminiProperties.getServerApiKey());
    }

    public boolean hasAnyKey(Users user) {
        return hasUserKey(user) || hasServerKey();
    }

    public void requireAnyKey(Users user) {
        if (!hasAnyKey(user)) {
            throw new AppException("Add your Gemini API key in Settings before indexing or asking.");
        }
    }

    public String requirePlaintext(Users user) {
        requireAnyKey(user);
        if (hasUserKey(user)) {
            return requireUserPlaintext(user);
        }
        return geminiProperties.getServerApiKey().trim();
    }

    public String requireUserPlaintext(Users user) {
        if (user == null || !StringUtils.hasText(user.getGeminiApiKey())) {
            throw new AppException("No Gemini API key is stored for this user.");
        }
        if (user.getUserID() != null) {
            String cached = decryptedKeys.getIfPresent(user.getUserID());
            if (StringUtils.hasText(cached)) {
                return cached;
            }
        }
        String stored = user.getGeminiApiKey();
        String plaintext = tokenCipher.decrypt(stored, user, TokenCipher.PURPOSE_GEMINI);
        if (!tokenCipher.isBound(stored) && user.getUserID() != null) {
            user.setGeminiApiKey(tokenCipher.encryptSecret(plaintext, user));
            user.setGeminiApiKeyHint(null);
            userRepository.save(user);
        }
        remember(user, plaintext);
        return plaintext;
    }

    public void encryptInto(Users user, String plaintext) {
        user.setGeminiApiKey(tokenCipher.encryptSecret(plaintext, user));
        user.setGeminiApiKeyHint(null);
        remember(user, plaintext);
    }

    public void clear(Users user) {
        user.setGeminiApiKey(null);
        user.setGeminiApiKeyHint(null);
        evict(user.getUserID());
    }

    public void remember(Users user, String plaintext) {
        if (user != null && user.getUserID() != null && StringUtils.hasText(plaintext)) {
            decryptedKeys.put(user.getUserID(), plaintext);
        }
    }

    public void evict(UUID userId) {
        if (userId != null) {
            decryptedKeys.invalidate(userId);
        }
    }

    public static String hint(String plaintext) {
        if (!StringUtils.hasText(plaintext) || plaintext.length() < 4) {
            return "••••";
        }
        return "…" + plaintext.substring(plaintext.length() - 4);
    }
}
