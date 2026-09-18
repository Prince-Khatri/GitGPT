package com.genai.gitgpt.rag.ingest;

import com.genai.gitgpt.rag.config.IndexProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceFileFilterAndChunkerTest {

    private final SourceFileFilter filter = new SourceFileFilter();

    @Test
    void keepsSourceAndDropsNoiseAndSecrets() {
        assertTrue(filter.keep("src/main/java/App.java", 1200, 256_000));
        assertTrue(filter.keep("README.md", 800, 256_000));
        assertFalse(filter.keep("node_modules/left-pad/index.js", 100, 256_000));
        assertFalse(filter.keep("target/classes/App.class", 100, 256_000));
        assertFalse(filter.keep(".env", 40, 256_000));
        assertFalse(filter.keep("secrets/api.key", 40, 256_000));
        assertFalse(filter.keep("logo.png", 200, 256_000));
        assertFalse(filter.keep("src/../.env", 40, 256_000));
        assertFalse(filter.keep("big.java", 300_000, 256_000));
    }

    @Test
    void chunksAreStableAndOverlap() {
        IndexProperties properties = new IndexProperties();
        properties.setChunkLines(4);
        properties.setChunkOverlapLines(1);
        Chunker chunker = new Chunker(properties);
        SourceFile file = new SourceFile("A.java", "java", "one\ntwo\nthree\nfour\nfive\nsix");
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID repoId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        List<CodeChunk> chunks = chunker.chunk(userId, repoId, "abc123", file);
        assertFalse(chunks.isEmpty());
        assertEquals(chunks.get(0).chunkId(), Chunker.stableId(userId, repoId, "abc123", "A.java", 0));
        assertEquals(1, chunks.get(0).startLine());
        assertTrue(chunks.get(0).text().contains("one"));
        if (chunks.size() > 1) {
            assertTrue(chunks.get(1).text().contains("four"));
        }
    }
}
