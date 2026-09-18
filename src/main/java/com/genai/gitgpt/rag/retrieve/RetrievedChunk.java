package com.genai.gitgpt.rag.retrieve;

public record RetrievedChunk(
        String id,
        String path,
        String language,
        int startLine,
        int endLine,
        String commitSha,
        String text,
        String source,
        double score
) {
    public boolean overlaps(RetrievedChunk other) {
        if (other == null || path == null || !path.equals(other.path)) {
            return false;
        }
        return startLine <= other.endLine && other.startLine <= endLine;
    }
}
