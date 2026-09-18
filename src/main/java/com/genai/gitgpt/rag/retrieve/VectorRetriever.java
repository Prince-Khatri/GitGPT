package com.genai.gitgpt.rag.retrieve;

import com.genai.gitgpt.rag.config.AskProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VectorRetriever {

    private final VectorStore vectorStore;
    private final AskProperties askProperties;

    public List<RetrievedChunk> search(UUID userId, UUID repoId, String commitSha, String query, List<String> pathHints) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op tenant = builder.and(
                builder.eq("userId", userId.toString()),
                builder.eq("repoId", repoId.toString())
        );
        FilterExpressionBuilder.Op filter = commitSha == null || commitSha.isBlank()
                ? tenant
                : builder.and(tenant, builder.eq("commitSha", commitSha));
        try {
            List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(query)
                    .topK(askProperties.getVectorTopK())
                    .filterExpression(filter.build())
                    .build());
            List<RetrievedChunk> chunks = new ArrayList<>();
            if (documents == null) {
                return chunks;
            }
            for (Document document : documents) {
                chunks.add(toChunk(document, pathHints));
            }
            return chunks;
        } catch (Exception ex) {
            log.warn("Vector search failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private static RetrievedChunk toChunk(Document document, List<String> pathHints) {
        Map<String, Object> metadata = document.getMetadata() == null ? Map.of() : document.getMetadata();
        String path = string(metadata, "path");
        double score = document.getScore() == null ? 0.0 : document.getScore();
        if (pathHints != null) {
            for (String hint : pathHints) {
                if (!hint.isBlank() && path.toLowerCase().contains(hint.toLowerCase())) {
                    score += 0.15;
                    break;
                }
            }
        }
        return new RetrievedChunk(
                document.getId(),
                path,
                string(metadata, "language"),
                integer(metadata, "startLine"),
                integer(metadata, "endLine"),
                string(metadata, "commitSha"),
                document.getText() == null ? "" : document.getText(),
                "VECTOR",
                score
        );
    }

    private static String string(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static int integer(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
