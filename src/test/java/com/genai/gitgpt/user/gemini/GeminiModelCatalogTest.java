package com.genai.gitgpt.user.gemini;

import com.genai.gitgpt.exception.AppException;
import com.genai.gitgpt.user.config.GeminiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiModelCatalogTest {

    private GeminiModelCatalog catalog;

    @BeforeEach
    void setUp() {
        GeminiProperties properties = new GeminiProperties();
        catalog = new GeminiModelCatalog(properties);
    }

    @Test
    void acceptsKnownChatAndEmbeddingModels() {
        assertEquals("gemini-3.5-flash", catalog.requireChat("gemini-3.5-flash"));
        assertEquals("gemini-3.6-flash", catalog.requireChat("models/gemini-3.6-flash"));
        assertEquals("gemini-embedding-001", catalog.requireEmbedding("gemini-embedding-001"));
    }

    @Test
    void rejectsUnknownModels() {
        assertThrows(AppException.class, () -> catalog.requireChat("gpt-4o"));
        assertThrows(AppException.class, () -> catalog.requireChat("gemini-3.6-flash-image"));
        assertThrows(AppException.class, () -> catalog.requireEmbedding("text-embedding-004"));
    }

    @Test
    void fallsBackToDefaultsForBlankStoredValues() {
        assertEquals("gemini-3.5-flash-lite", catalog.resolveChat(""));
        assertEquals("gemini-embedding-001", catalog.resolveEmbedding(null));
        assertTrue(catalog.chatModels().size() >= 2);
        assertEquals(1, catalog.embeddingModels().size());
    }
}
