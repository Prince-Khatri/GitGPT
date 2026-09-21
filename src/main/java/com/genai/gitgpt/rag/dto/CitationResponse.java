package com.genai.gitgpt.rag.dto;

public record CitationResponse(
        String path,
        int startLine,
        int endLine,
        String commitSha,
        String source,
        String githubUrl
) {
}
