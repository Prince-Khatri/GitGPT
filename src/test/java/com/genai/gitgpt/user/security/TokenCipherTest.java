package com.genai.gitgpt.user.security;

import com.genai.gitgpt.exception.AppException;
import com.genai.gitgpt.user.config.SecurityProperties;
import com.genai.gitgpt.user.models.Users;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenCipherTest {

    static final String TEST_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private TokenCipher cipher;
    private Users user;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties();
        properties.setTokenEncryptionKey(TEST_KEY);
        cipher = new TokenCipher(properties);
        cipher.init();
        user = Users.builder()
                .userID(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .githubId("42")
                .email("dev@example.com")
                .build();
    }

    @Test
    void roundTripKeepsThePlaintext() {
        String encrypted = cipher.encrypt("gho_test-token-value", user);
        assertTrue(cipher.isEncrypted(encrypted));
        assertTrue(cipher.isBound(encrypted));
        assertTrue(encrypted.startsWith(TokenCipher.PREFIX_V2));
        assertNotEquals("gho_test-token-value", encrypted);
        assertEquals("gho_test-token-value", cipher.decrypt(encrypted, user, TokenCipher.PURPOSE_GITHUB));
    }

    @Test
    void decryptsLegacyV1CiphertextWithoutAad() throws Exception {
        byte[] iv = new byte[12];
        java.util.Arrays.fill(iv, (byte) 7);
        Cipher raw = Cipher.getInstance("AES/GCM/NoPadding");
        raw.init(
                Cipher.ENCRYPT_MODE,
                new SecretKeySpec(TokenCipher.decodeKey(TEST_KEY), "AES"),
                new GCMParameterSpec(128, iv)
        );
        byte[] cipherText = raw.doFinal("gho_legacy_v1".getBytes(StandardCharsets.UTF_8));
        ByteBuffer packed = ByteBuffer.allocate(iv.length + cipherText.length);
        packed.put(iv);
        packed.put(cipherText);
        String stored = TokenCipher.PREFIX_V1 + Base64.getEncoder().encodeToString(packed.array());

        assertFalse(cipher.isBound(stored));
        assertEquals("gho_legacy_v1", cipher.decrypt(stored, user, TokenCipher.PURPOSE_GITHUB));
    }

    @Test
    void decryptLeavesLegacyPlaintextUnchanged() {
        assertFalse(cipher.isEncrypted("gho_legacy"));
        assertEquals("gho_legacy", cipher.decrypt("gho_legacy", user, TokenCipher.PURPOSE_GITHUB));
    }

    @Test
    void otherUserCannotDecrypt() {
        String encrypted = cipher.encrypt("gho_secret", user);
        Users other = Users.builder().githubId("99").email("other@example.com").build();
        assertThrows(AppException.class, () -> cipher.decrypt(encrypted, other, TokenCipher.PURPOSE_GITHUB));
    }

    @Test
    void geminiCiphertextCannotBeReadAsGithubToken() {
        String encrypted = cipher.encryptSecret("AIza-fake-gemini-key-value", user);
        assertThrows(AppException.class, () -> cipher.decrypt(encrypted, user, TokenCipher.PURPOSE_GITHUB));
        assertEquals("AIza-fake-gemini-key-value", cipher.decrypt(encrypted, user, TokenCipher.PURPOSE_GEMINI));
    }

    @Test
    void acceptsHexAndPassphraseKeys() {
        byte[] fromHex = TokenCipher.decodeKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        assertEquals(32, fromHex.length);
        byte[] fromPhrase = TokenCipher.decodeKey("local-dev-passphrase");
        assertEquals(32, fromPhrase.length);
        assertNotEquals(java.util.Arrays.toString(fromHex), java.util.Arrays.toString(fromPhrase));
    }

    @Test
    void wrongKeyCannotDecrypt() {
        String encrypted = cipher.encrypt("gho_secret", user);
        SecurityProperties other = new SecurityProperties();
        other.setTokenEncryptionKey(Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes()));
        TokenCipher otherCipher = new TokenCipher(other);
        otherCipher.init();
        assertThrows(AppException.class, () -> otherCipher.decrypt(encrypted, user, TokenCipher.PURPOSE_GITHUB));
    }
}
