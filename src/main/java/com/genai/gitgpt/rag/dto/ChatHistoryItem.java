package com.genai.gitgpt.rag.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChatHistoryItem(
        UUID sessionId,
        UUID repoId,
        String repoFullName,
        String title,
        String commitSha,
        LocalDateTime updatedAt
) {
}
