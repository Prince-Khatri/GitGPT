package com.genai.gitgpt.rag.retrieve;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genai.gitgpt.rag.config.AskProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@Slf4j
public class CandidateFinder {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AskProperties askProperties;
    private final String qualifiedTable;

    public CandidateFinder(
            JdbcTemplate jdbcTemplate,
            AskProperties askProperties,
            @Value("${spring.ai.vectorstore.pgvector.schema-name}") String schema,
            @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store}") String table
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
        this.askProperties = askProperties;
        this.qualifiedTable = quote(schema) + "." + quote(table);
    }

    public List<RetrievedChunk> find(UUID userId, UUID repoId, String commitSha, QueryPlan plan) {
        List<String> terms = distinct(concat(plan.keywords(), plan.symbolHints(), plan.pathHints()));
        if (terms.isEmpty() && plan.languages().isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT id::text AS id, content, metadata
                FROM %s
                WHERE metadata->>'userId' = ?
                  AND metadata->>'repoId' = ?
                """.formatted(qualifiedTable));
        List<Object> args = new ArrayList<>();
        args.add(userId.toString());
        args.add(repoId.toString());
        if (commitSha != null && !commitSha.isBlank()) {
            sql.append(" AND metadata->>'commitSha' = ? ");
            args.add(commitSha);
        }
        if (!plan.languages().isEmpty()) {
            sql.append(" AND metadata->>'language' IN (");
            appendPlaceholders(sql, plan.languages().size());
            sql.append(") ");
            args.addAll(plan.languages().stream().map(IdentifierExtractor::lower).toList());
        }
        if (!terms.isEmpty()) {
            sql.append(" AND (");
            for (int i = 0; i < terms.size(); i++) {
                if (i > 0) {
                    sql.append(" OR ");
                }
                sql.append("(content ILIKE ? OR metadata->>'path' ILIKE ?)");
                String like = "%" + terms.get(i) + "%";
                args.add(like);
                args.add(like);
            }
            sql.append(") ");
        }
        sql.append(" LIMIT ? ");
        args.add(askProperties.getKeywordLimit());
        try {
            return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> toChunk(
                    rs.getString("id"),
                    rs.getString("content"),
                    rs.getString("metadata"),
                    "KEYWORD",
                    1.0 + Math.max(0, 0.02 * (askProperties.getKeywordLimit() - rowNum))
            ), args.toArray());
        } catch (Exception ex) {
            log.warn("Keyword candidate search failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private RetrievedChunk toChunk(String id, String content, String metadataJson, String source, double score) {
        Map<String, Object> metadata = Map.of();
        try {
            if (metadataJson != null && !metadataJson.isBlank()) {
                metadata = objectMapper.readValue(metadataJson, MAP_TYPE);
            }
        } catch (Exception ignored) {
            metadata = Map.of();
        }
        return new RetrievedChunk(
                id,
                string(metadata, "path"),
                string(metadata, "language"),
                integer(metadata, "startLine"),
                integer(metadata, "endLine"),
                string(metadata, "commitSha"),
                content == null ? "" : content,
                source,
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

    private static void appendPlaceholders(StringBuilder sql, int count) {
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append('?');
        }
    }

    @SafeVarargs
    private static List<String> concat(List<String>... lists) {
        List<String> all = new ArrayList<>();
        for (List<String> list : lists) {
            if (list != null) {
                all.addAll(list);
            }
        }
        return all;
    }

    private static List<String> distinct(List<String> values) {
        return values.stream().map(IdentifierExtractor::sanitize).filter(v -> !v.isBlank()).distinct().limit(8).toList();
    }

    private static String quote(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid SQL identifier");
        }
        return identifier;
    }
}
