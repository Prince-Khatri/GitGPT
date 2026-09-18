package com.genai.gitgpt.rag.retrieve;

import com.genai.gitgpt.rag.config.AskProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ContextPacker {

    private final AskProperties askProperties;

    public List<RetrievedChunk> pack(List<RetrievedChunk> keywordHits, List<RetrievedChunk> vectorHits) {
        Map<String, RetrievedChunk> merged = new LinkedHashMap<>();
        addAll(merged, keywordHits);
        addAll(merged, vectorHits);
        List<RetrievedChunk> ranked = new ArrayList<>(merged.values());
        ranked.sort(Comparator.comparingDouble(RetrievedChunk::score).reversed());
        List<RetrievedChunk> selected = new ArrayList<>();
        int tokens = 0;
        int maxChunks = Math.max(1, askProperties.getPackedChunks());
        int maxTokens = Math.max(500, askProperties.getMaxContextTokens());
        for (RetrievedChunk chunk : ranked) {
            if (selected.size() >= maxChunks) {
                break;
            }
            if (overlaps(selected, chunk)) {
                continue;
            }
            int chunkTokens = Math.max(1, chunk.text().length() / 4);
            if (!selected.isEmpty() && tokens + chunkTokens > maxTokens) {
                continue;
            }
            selected.add(chunk);
            tokens += chunkTokens;
        }
        return selected;
    }

    public String format(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "(no matching chunks in the indexed snapshot)";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk chunk = chunks.get(i);
            builder.append('[')
                    .append(i + 1)
                    .append("] ")
                    .append(chunk.path())
                    .append(" L")
                    .append(chunk.startLine())
                    .append('-')
                    .append(chunk.endLine())
                    .append(" sha=")
                    .append(chunk.commitSha())
                    .append('\n')
                    .append(chunk.text())
                    .append("\n\n");
        }
        return builder.toString().trim();
    }

    private static void addAll(Map<String, RetrievedChunk> merged, List<RetrievedChunk> chunks) {
        if (chunks == null) {
            return;
        }
        for (RetrievedChunk chunk : chunks) {
            String key = chunk.id() == null || chunk.id().isBlank()
                    ? chunk.path() + ":" + chunk.startLine() + ":" + chunk.endLine()
                    : chunk.id();
            RetrievedChunk existing = merged.get(key);
            if (existing == null || chunk.score() > existing.score()) {
                merged.put(key, chunk);
            }
        }
    }

    private static boolean overlaps(List<RetrievedChunk> selected, RetrievedChunk candidate) {
        for (RetrievedChunk chunk : selected) {
            if (chunk.overlaps(candidate)) {
                return true;
            }
        }
        return false;
    }
}
