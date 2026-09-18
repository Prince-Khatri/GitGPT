package com.genai.gitgpt.user.security;

import com.genai.gitgpt.exception.AppException;
import com.genai.gitgpt.user.config.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenCipherTest {

    static final String TEST_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private TokenCipher cipher;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties();
        properties.setTokenEncryptionKey(TEST_KEY);
        cipher = new TokenCipher(properties);
        cipher.init();
    }

    @Test
    void roundTripKeepsThePlaintext() {
        String encrypted = cipher.encrypt("gho_test-token-value");
        assertTrue(cipher.isEncrypted(encrypted));
        assertTrue(encrypted.startsWith(TokenCipher.PREFIX));
        assertNotEquals("gho_test-token-value", encrypted);
        assertEquals("gho_test-token-value", cipher.decrypt(encrypted));
    }

    @Test
    void decryptLeavesLegacyPlaintextUnchanged() {
        assertFalse(cipher.isEncrypted("gho_legacy"));
        assertEquals("gho_legacy", cipher.decrypt("gho_legacy"));
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
        String encrypted = cipher.encrypt("gho_secret");
        SecurityProperties other = new SecurityProperties();
        other.setTokenEncryptionKey(Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes()));
        TokenCipher otherCipher = new TokenCipher(other);
        otherCipher.init();
        assertThrows(AppException.class, () -> otherCipher.decrypt(encrypted));
    }
}
