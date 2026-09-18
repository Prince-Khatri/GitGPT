package com.genai.gitgpt.rag.dto;

import java.util.List;

public record CitationResponse(
        String path,
        int startLine,
        int endLine,
        String commitSha,
        String source
) {
}
