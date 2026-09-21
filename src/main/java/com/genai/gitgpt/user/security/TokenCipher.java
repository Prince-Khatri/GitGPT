package com.genai.gitgpt.user.security;

import com.genai.gitgpt.exception.AppException;
import com.genai.gitgpt.user.config.SecurityProperties;
import com.genai.gitgpt.user.models.Users;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * AES-256-GCM for GitHub tokens and Gemini keys. New writes are {@code enc:v2:}
 * bound to purpose + GitHub id so ciphertext cannot be copied between users or columns.
 * {@code enc:v1:} and leftover plaintext still decrypt, then callers re-encrypt.
 */
@Slf4j
@Component
public class TokenCipher {

    static final String PREFIX_V1 = "enc:v1:";
    static final String PREFIX_V2 = "enc:v2:";
    static final String PREFIX = PREFIX_V1;
    public static final String PURPOSE_GITHUB = "github-token";
    public static final String PURPOSE_GEMINI = "gemini-api-key";
    static final String LOCAL_DEV_KEY = "gitgpt-local-dev-only-not-for-prod";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;
    private static final Pattern HEX_KEY = Pattern.compile("(?i)[0-9a-f]{64}");

    private final SecurityProperties properties;
    private final SecureRandom random = new SecureRandom();
    private SecretKeySpec key;

    public TokenCipher(SecurityProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        String configured = properties.getTokenEncryptionKey();
        if (LOCAL_DEV_KEY.equals(configured)) {
            log.warn("Using the committed local encryption key. Set GITGPT_TOKEN_ENCRYPTION_KEY "
                    + "(openssl rand -base64 32) before storing real tokens.");
        }
        this.key = new SecretKeySpec(decodeKey(configured), "AES");
    }

    public String encrypt(String plaintext, Users user) {
        return encrypt(plaintext, PURPOSE_GITHUB, subject(user),
                "A GitHub access token is required.",
                "Could not encrypt the GitHub access token.");
    }

    public String encryptSecret(String plaintext, Users user) {
        return encrypt(plaintext, PURPOSE_GEMINI, subject(user),
                "A Gemini API key is required.",
                "Could not encrypt the Gemini API key.");
    }

    public String encrypt(String plaintext) {
        return encrypt(plaintext, PURPOSE_GITHUB, "unbound",
                "A GitHub access token is required.",
                "Could not encrypt the GitHub access token.");
    }

    public String encryptSecret(String plaintext) {
        return encrypt(plaintext, PURPOSE_GEMINI, "unbound",
                "A Gemini API key is required.",
                "Could not encrypt the Gemini API key.");
    }

    private String encrypt(
            String plaintext,
            String purpose,
            String subject,
            String missingMessage,
            String failureMessage
    ) {
        requireKey();
        if (!StringUtils.hasText(plaintext)) {
            throw new AppException(missingMessage);
        }
        if (!StringUtils.hasText(subject)) {
            throw new AppException("Cannot encrypt a secret without a user identity.");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(purpose, subject));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer packed = ByteBuffer.allocate(iv.length + cipherText.length);
            packed.put(iv);
            packed.put(cipherText);
            return PREFIX_V2 + Base64.getEncoder().encodeToString(packed.array());
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(failureMessage);
        }
    }

    public String decrypt(String stored, Users user, String purpose) {
        return decrypt(stored, purpose, subject(user));
    }

    public String decrypt(String stored) {
        return decrypt(stored, PURPOSE_GITHUB, "unbound");
    }

    String decrypt(String stored, String purpose, String subject) {
        if (!StringUtils.hasText(stored)) {
            throw new AppException("No secret is stored for this user.");
        }
        if (!isEncrypted(stored)) {
            return stored;
        }
        requireKey();
        boolean bound = isBound(stored);
        String prefix = bound ? PREFIX_V2 : PREFIX_V1;
        try {
            byte[] packed = Base64.getDecoder().decode(stored.substring(prefix.length()));
            if (packed.length < IV_BYTES + 16) {
                throw new IllegalArgumentException("ciphertext too short");
            }
            ByteBuffer buffer = ByteBuffer.wrap(packed);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            if (bound) {
                if (!StringUtils.hasText(purpose) || !StringUtils.hasText(subject)) {
                    throw new IllegalArgumentException("bound ciphertext needs purpose and subject");
                }
                cipher.updateAAD(aad(purpose, subject));
            }
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(
                    "Could not decrypt the stored secret. Use the same GITGPT_TOKEN_ENCRYPTION_KEY and try again."
            );
        }
    }

    public boolean isEncrypted(String stored) {
        return stored != null && (stored.startsWith(PREFIX_V1) || stored.startsWith(PREFIX_V2));
    }

    public boolean isBound(String stored) {
        return stored != null && stored.startsWith(PREFIX_V2);
    }

    static String subject(Users user) {
        if (user != null && StringUtils.hasText(user.getGithubId())) {
            return "github:" + user.getGithubId();
        }
        if (user != null && user.getUserID() != null) {
            return "user:" + user.getUserID();
        }
        throw new AppException("Cannot encrypt a secret without a user identity.");
    }

    private static byte[] aad(String purpose, String subject) {
        return (purpose + "\0" + subject).getBytes(StandardCharsets.UTF_8);
    }

    private void requireKey() {
        if (key == null) {
            throw new AppException("GITGPT_TOKEN_ENCRYPTION_KEY is not configured.");
        }
    }

    static byte[] decodeKey(String encoded) {
        if (!StringUtils.hasText(encoded)) {
            throw new IllegalStateException(
                    "GITGPT_TOKEN_ENCRYPTION_KEY is required. Add it to the IntelliJ run configuration "
                            + "(Run → Edit Configurations → Environment variables) or export it in the shell. "
                            + "Preferred: openssl rand -base64 32"
            );
        }
        String normalized = encoded.replaceAll("\\s+", "");
        byte[] fromBase64 = tryBase64(normalized);
        if (fromBase64 != null && fromBase64.length == KEY_BYTES) {
            return fromBase64;
        }
        if (HEX_KEY.matcher(normalized).matches()) {
            return HexFormat.of().parseHex(normalized);
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(encoded.trim().getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required to derive the token encryption key.");
        }
    }

    private static byte[] tryBase64(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
