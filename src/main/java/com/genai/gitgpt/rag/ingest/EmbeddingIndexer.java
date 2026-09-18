package com.genai.gitgpt.rag.ingest;

import com.genai.gitgpt.exception.AppException;
import com.genai.gitgpt.rag.config.IndexProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingIndexer {

    private final VectorStore vectorStore;
    private final IndexProperties properties;

    public int upsert(UUID userId, UUID repoId, String commitSha, List<CodeChunk> chunks) {
        deleteRepoChunks(userId, repoId);
        if (chunks.isEmpty()) {
            return 0;
        }
        List<Document> documents = chunks.stream()
                .map(chunk -> toDocument(userId, repoId, commitSha, chunk))
                .toList();
        int batchSize = Math.max(1, properties.getEmbedBatchSize());
        for (int i = 0; i < documents.size(); i += batchSize) {
            List<Document> batch = documents.subList(i, Math.min(documents.size(), i + batchSize));
            addWithRetry(batch, userId, repoId);
        }
        return documents.size();
    }

    private void deleteRepoChunks(UUID userId, UUID repoId) {
        String filter = "userId == '" + userId + "' && repoId == '" + repoId + "'";
        try {
            vectorStore.delete(filter);
        } catch (Exception ex) {
            log.warn("Could not delete existing vectors for repo {}: {}", repoId, ex.getMessage());
        }
    }

    private void addWithRetry(List<Document> batch, UUID userId, UUID repoId) {
        int attempts = Math.max(1, properties.getEmbedMaxAttempts());
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                vectorStore.add(new ArrayList<>(batch));
                return;
            } catch (Exception ex) {
                if (attempt == attempts || !retryable(ex)) {
                    throw new AppException("Failed to embed repository chunks: " + ex.getMessage(), ex);
                }
                long sleepMs = 1000L * (1L << (attempt - 1));
                log.warn("Embedding batch failed for repo {} (attempt {}/{}). Retrying in {} ms",
                        repoId, attempt, attempts, sleepMs);
                sleep(sleepMs);
            }
        }
    }

    private Document toDocument(UUID userId, UUID repoId, String commitSha, CodeChunk chunk) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("userId", userId.toString());
        metadata.put("repoId", repoId.toString());
        metadata.put("commitSha", commitSha);
        metadata.put("path", chunk.path());
        metadata.put("language", chunk.language());
        metadata.put("startLine", chunk.startLine());
        metadata.put("endLine", chunk.endLine());
        metadata.put("chunkIndex", chunk.chunkIndex());
        metadata.put("tokenCount", chunk.tokenCount());
        return Document.builder()
                .id(chunk.chunkId().toString())
                .text(chunk.text())
                .metadata(metadata)
                .build();
    }

    private static boolean retryable(Throwable ex) {
        String message = String.valueOf(ex.getMessage()).toLowerCase();
        Throwable cause = ex;
        while (cause != null) {
            message = message + " " + String.valueOf(cause.getMessage()).toLowerCase();
            cause = cause.getCause();
        }
        return message.contains("429")
                || message.contains("rate")
                || message.contains("resource_exhausted")
                || message.contains("unavailable")
                || message.contains("503");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AppException("Embedding was interrupted.", ex);
        }
    }
}
