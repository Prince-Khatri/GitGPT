package com.genai.gitgpt.user.gemini;

import com.genai.gitgpt.user.config.GeminiProperties;
import com.google.genai.types.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiModelListerTest {

    private GeminiModelLister lister;

    @BeforeEach
    void setUp() {
        GeminiModelCatalog catalog = new GeminiModelCatalog(new GeminiProperties());
        lister = new GeminiModelLister(null, catalog);
    }

    @Test
    void keepsChatModelsAndDropsImageAndEmbed() {
        Optional<GeminiModelOption> flash = lister.toChatOption(model("models/gemini-3.6-flash", "Gemini 3.6 Flash", List.of("generateContent")));
        Optional<GeminiModelOption> image = lister.toChatOption(model("models/gemini-3.6-flash-image", "Image", List.of("generateContent")));
        Optional<GeminiModelOption> embed = lister.toChatOption(model("models/gemini-embedding-001", "Embed", List.of("embedContent")));

        assertTrue(flash.isPresent());
        assertEquals("gemini-3.6-flash", flash.get().id());
        assertTrue(image.isEmpty());
        assertTrue(embed.isEmpty());
    }

    @Test
    void restListKeepsGenerateContentModels() {
        List<GeminiModelOption> models = lister.parseRestModels("""
                {"models":[
                  {"name":"models/gemini-2.5-flash","displayName":"Gemini 2.5 Flash",
                   "supportedGenerationMethods":["generateContent","countTokens"]},
                  {"name":"models/gemini-embedding-001","supportedGenerationMethods":["embedContent"]},
                  {"name":"models/gemini-3.6-flash-image","supportedGenerationMethods":["generateContent"]}
                ]}
                """);
        assertEquals(1, models.size());
        assertEquals("gemini-2.5-flash", models.get(0).id());
    }

    private static Model model(String name, String displayName, List<String> actions) {
        return Model.builder()
                .name(name)
                .displayName(displayName)
                .supportedActions(actions)
                .build();
    }
}
