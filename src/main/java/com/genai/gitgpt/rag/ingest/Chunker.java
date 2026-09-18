package com.genai.gitgpt.rag.ingest;

import com.genai.gitgpt.rag.config.IndexProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class Chunker {

    private final IndexProperties properties;

    public List<CodeChunk> chunk(UUID userId, UUID repoId, String commitSha, SourceFile file) {
        String[] lines = file.content().split("\n", -1);
        int window = Math.max(20, properties.getChunkLines());
        int overlap = Math.min(Math.max(0, properties.getChunkOverlapLines()), window - 1);
        int step = Math.max(1, window - overlap);

        List<CodeChunk> chunks = new ArrayList<>();
        int index = 0;
        int start = 0;
        while (start < lines.length) {
            int end = Math.min(lines.length, start + window);
            String text = join(lines, start, end).trim();
            if (!text.isBlank()) {
                int startLine = start + 1;
                int endLine = end;
                UUID chunkId = stableId(userId, repoId, commitSha, file.path(), index);
                chunks.add(new CodeChunk(
                        chunkId,
                        file.path(),
                        file.language(),
                        startLine,
                        endLine,
                        index,
                        text,
                        tokenCount(text)
                ));
                index++;
            }
            if (end >= lines.length) {
                break;
            }
            start += step;
        }
        return chunks;
    }

    static UUID stableId(UUID userId, UUID repoId, String commitSha, String path, int chunkIndex) {
        String key = userId + "|" + repoId + "|" + commitSha + "|" + path + "|" + chunkIndex;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    static int tokenCount(String text) {
        return Math.max(1, text.length() / 4);
    }

    private static String join(String[] lines, int start, int end) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i > start) {
                builder.append('\n');
            }
            builder.append(lines[i]);
        }
        return builder.toString();
    }
}
