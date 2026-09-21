package com.genai.gitgpt.user.gemini;

public record GeminiSettingsRequest(
        String apiKey,
        String chatModel,
        String embeddingModel
) {
}
