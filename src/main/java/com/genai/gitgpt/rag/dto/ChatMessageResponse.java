package com.genai.gitgpt.rag.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(
        UUID messageId,
        String role,
        String content,
        String intent,
        Boolean grounded,
        List<CitationResponse> citations,
        LocalDateTime createdAt
) {
}
