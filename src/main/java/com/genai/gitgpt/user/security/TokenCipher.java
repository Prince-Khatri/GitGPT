package com.genai.gitgpt.user.security;

import com.genai.gitgpt.exception.AppException;
import com.genai.gitgpt.user.config.SecurityProperties;
import jakarta.annotation.PostConstruct;
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

@Component
public class TokenCipher {

    static final String PREFIX = "enc:v1:";
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
        this.key = new SecretKeySpec(decodeKey(properties.getTokenEncryptionKey()), "AES");
    }

    public String encrypt(String plaintext) {
        requireKey();
        if (!StringUtils.hasText(plaintext)) {
            throw new AppException("A GitHub access token is required.");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer packed = ByteBuffer.allocate(iv.length + cipherText.length);
            packed.put(iv);
            packed.put(cipherText);
            return PREFIX + Base64.getEncoder().encodeToString(packed.array());
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException("Could not encrypt the GitHub access token.");
        }
    }

    public String decrypt(String stored) {
        if (!StringUtils.hasText(stored)) {
            throw new AppException("No GitHub access token is stored for this user.");
        }
        if (!isEncrypted(stored)) {
            return stored;
        }
        requireKey();
        try {
            byte[] packed = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
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
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(
                    "Could not decrypt the stored GitHub token. Use the same GITGPT_TOKEN_ENCRYPTION_KEY and sign in again."
            );
        }
    }

    public boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX);
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
