package com.genai.gitgpt.user.gemini;

import com.genai.gitgpt.exception.AppException;
import com.genai.gitgpt.user.config.GeminiProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class GeminiModelCatalog {

    private static final List<GeminiModelOption> CHAT_MODELS = List.of(
            new GeminiModelOption("gemini-3.8-flash", "Gemini 3.8 Flash", "Latest Flash. Strong for coding and long tasks."),
            new GeminiModelOption("gemini-3.7-flash", "Gemini 3.7 Flash", "Previous Flash. Agentic and multi-step."),
            new GeminiModelOption("gemini-3.6-flash", "Gemini 3.6 Flash", "Fast Flash for everyday and coding tasks."),
            new GeminiModelOption("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Fastest and cheapest. Default for answers."),
            new GeminiModelOption("gemini-3.5-flash", "Gemini 3.5 Flash", "Balanced speed and quality."),
            new GeminiModelOption("gemini-3.1-flash-lite", "Gemini 3.1 Flash-Lite", "Earlier lite Flash."),
            new GeminiModelOption("gemini-3.1-pro-preview", "Gemini 3.1 Pro", "Stronger reasoning. Preview."),
            new GeminiModelOption("gemini-3-pro", "Gemini 3 Pro", "Earlier Pro. May not be on every key."),
            new GeminiModelOption("gemini-2.5-flash", "Gemini 2.5 Flash", "Previous Flash generation."),
            new GeminiModelOption("gemini-2.5-pro", "Gemini 2.5 Pro", "Previous Pro generation."),
            new GeminiModelOption("gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite", "Previous lite model."),
            new GeminiModelOption("gemini-2.0-flash", "Gemini 2.0 Flash", "Gemini 2.0 Flash.")
    );

    private static final List<GeminiModelOption> EMBEDDING_MODELS = List.of(
            new GeminiModelOption(
                    "gemini-embedding-001",
                    "Gemini Embedding 001",
                    "Required 1536-dimension embeddings for this app's vector store."
            )
    );

    private final GeminiProperties properties;

    public GeminiModelCatalog(GeminiProperties properties) {
        this.properties = properties;
    }

    public List<GeminiModelOption> chatModels() {
        return CHAT_MODELS;
    }

    public List<GeminiModelOption> embeddingModels() {
        return EMBEDDING_MODELS;
    }

    public Optional<GeminiModelOption> knownChat(String modelId) {
        String id = normalizeId(modelId);
        return CHAT_MODELS.stream().filter(option -> option.id().equals(id)).findFirst();
    }

    public String defaultChatModel() {
        return requireChat(properties.getDefaultChatModel());
    }

    public String defaultEmbeddingModel() {
        return requireEmbedding(properties.getDefaultEmbeddingModel());
    }

    public String requireChat(String model) {
        String resolved = StringUtils.hasText(model)
                ? normalizeId(model)
                : normalizeId(StringUtils.hasText(properties.getDefaultChatModel())
                        ? properties.getDefaultChatModel()
                        : "gemini-3.5-flash-lite");
        if (!isChatModelId(resolved)) {
            throw new AppException("Choose a supported Gemini chat model.");
        }
        return resolved;
    }

    public String requireEmbedding(String model) {
        String resolved = StringUtils.hasText(model) ? normalizeId(model) : defaultOrFirst(EMBEDDING_MODELS, "gemini-embedding-001");
        if (EMBEDDING_MODELS.stream().noneMatch(option -> option.id().equals(resolved))) {
            throw new AppException(
                    "Choose a supported embedding model. GitGPT stores vectors at "
                            + properties.getEmbeddingDimensions()
                            + " dimensions, so only compatible Gemini embedding models are listed."
            );
        }
        return resolved;
    }

    public String resolveChat(String stored) {
        try {
            return requireChat(stored);
        } catch (AppException ex) {
            return defaultChatModel();
        }
    }

    public String resolveEmbedding(String stored) {
        try {
            return requireEmbedding(stored);
        } catch (AppException ex) {
            return defaultEmbeddingModel();
        }
    }

    static boolean isChatModelId(String modelId) {
        String id = normalizeId(modelId);
        if (!id.startsWith("gemini-")) {
            return false;
        }
        return !isNonChatGemini(id);
    }

    static String normalizeId(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return "";
        }
        String id = modelId.trim();
        if (id.startsWith("models/")) {
            id = id.substring("models/".length());
        }
        return id;
    }

    static String prettyLabel(String modelId) {
        String id = normalizeId(modelId);
        if (id.isBlank()) {
            return "Gemini";
        }
        String rest = id.startsWith("gemini-") ? id.substring("gemini-".length()) : id;
        StringBuilder label = new StringBuilder("Gemini");
        for (String part : rest.split("-")) {
            if (part.isBlank() || "preview".equalsIgnoreCase(part)) {
                continue;
            }
            label.append(' ');
            if (part.length() == 1) {
                label.append(part.toUpperCase(Locale.ROOT));
            } else {
                label.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        if (id.contains("preview")) {
            label.append(" (preview)");
        }
        return label.toString();
    }

    private static boolean isNonChatGemini(String id) {
        return containsAny(
                id,
                "embed",
                "image",
                "live",
                "tts",
                "transcribe",
                "audio",
                "computer-use",
                "robotics",
                "omni",
                "imagen",
                "veo",
                "lyria"
        );
    }

    private static boolean containsAny(String id, String... needles) {
        for (String needle : needles) {
            if (id.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String defaultOrFirst(List<GeminiModelOption> options, String fallback) {
        return options.isEmpty() ? fallback : options.get(0).id();
    }
}
