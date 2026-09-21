package com.genai.gitgpt.user.gemini;

import java.util.List;

public record GeminiSettingsResponse(
        boolean hasUserKey,
        boolean hasServerFallback,
        String keyHint,
        String chatModel,
        String embeddingModel,
        int embeddingDimensions,
        List<GeminiModelOption> chatModels,
        List<GeminiModelOption> embeddingModels
) {
}
