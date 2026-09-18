package com.genai.gitgpt.rag.dto;

import java.util.List;
import java.util.UUID;

public record AskResponse(
        UUID repoId,
        String repoFullName,
        String question,
        String intent,
        String rewrittenQuery,
        String answer,
        boolean grounded,
        List<CitationResponse> citations
) {
}
