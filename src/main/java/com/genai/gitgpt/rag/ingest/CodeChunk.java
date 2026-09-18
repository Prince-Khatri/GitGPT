package com.genai.gitgpt.rag.ingest;

import java.util.Set;
import java.util.UUID;

public record CodeChunk(
        UUID chunkId,
        String path,
        String language,
        int startLine,
        int endLine,
        int chunkIndex,
        String text,
        int tokenCount
) {
}
