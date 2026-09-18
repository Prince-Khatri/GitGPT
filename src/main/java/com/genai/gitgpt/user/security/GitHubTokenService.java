package com.genai.gitgpt.user.security;

import com.genai.gitgpt.exception.AppException;
import com.genai.gitgpt.user.config.SecurityProperties;
import com.genai.gitgpt.user.models.Users;
import com.genai.gitgpt.user.repository.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

@Service
public class GitHubTokenService {

    private final TokenCipher tokenCipher;
    private final UserRepository userRepository;
    private final Cache<UUID, String> decryptedTokens;

    public GitHubTokenService(
            TokenCipher tokenCipher,
            UserRepository userRepository,
            SecurityProperties properties
    ) {
        this.tokenCipher = tokenCipher;
        this.userRepository = userRepository;
        this.decryptedTokens = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(Math.max(1, properties.getTokenCacheTtlMinutes())))
                .maximumSize(10_000)
                .build();
    }

    public void encryptInto(Users user, String plaintext) {
        user.setAccessToken(tokenCipher.encrypt(plaintext));
        remember(user, plaintext);
    }

    public void remember(Users user, String plaintext) {
        if (user != null && user.getUserID() != null && StringUtils.hasText(plaintext)) {
            decryptedTokens.put(user.getUserID(), plaintext);
        }
    }

    public void evict(UUID userId) {
        if (userId != null) {
            decryptedTokens.invalidate(userId);
        }
    }

    public String requirePlaintext(Users user) {
        if (user == null) {
            throw new AppException("A saved GitHub user is required before GitHub can be called.");
        }
        if (user.getUserID() != null) {
            String cached = decryptedTokens.getIfPresent(user.getUserID());
            if (StringUtils.hasText(cached)) {
                return cached;
            }
        }
        String stored = user.getAccessToken();
        String plaintext = tokenCipher.decrypt(stored);
        if (!tokenCipher.isEncrypted(stored) && user.getUserID() != null) {
            user.setAccessToken(tokenCipher.encrypt(plaintext));
            userRepository.save(user);
        }
        remember(user, plaintext);
        return plaintext;
    }
}
