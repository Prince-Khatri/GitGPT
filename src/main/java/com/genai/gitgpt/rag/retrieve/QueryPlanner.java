package com.genai.gitgpt.rag.retrieve;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryPlanner {

    private static final String SYSTEM = """
            You plan retrieval for questions about a single GitHub repository.
            Return ONLY JSON with keys:
            intent (one of explain, locate, how_to_change, debug, architecture),
            rewrittenQuery (search-friendly English),
            keywords (array of exact identifiers or terms),
            pathHints (array of path fragments like src/main/java or security),
            languages (array of file extensions like java, md),
            symbolHints (array of class/method/field names).
            Do not answer the question. Do not invent repo files. No markdown.
            """;

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QueryPlan plan(String question) {
        QueryPlan fallback = QueryPlan.fallback(question);
        if (hasStrongIdentifiers(question)) {
            log.info("Skipping query planner; using extracted identifiers");
            return new QueryPlan(
                    "locate",
                    fallback.rewrittenQuery(),
                    fallback.keywords(),
                    fallback.pathHints(),
                    fallback.languages(),
                    fallback.symbolHints()
            );
        }
        try {
            String raw = chatModel.call(new Prompt(List.of(
                    new SystemMessage(SYSTEM),
                    new UserMessage(question)
            ))).getResult().getOutput().getText();
            QueryPlan parsed = parse(raw, fallback);
            return merge(parsed, fallback);
        } catch (Exception ex) {
            log.warn("Query planner failed, using fallback identifiers: {}", ex.getMessage());
            return fallback;
        }
    }

    public static boolean hasStrongIdentifiers(String question) {
        return !IdentifierExtractor.extract(question).isEmpty();
    }

    private QueryPlan parse(String raw, QueryPlan fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String json = raw.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return fallback;
        }
        try {
            JsonNode node = objectMapper.readTree(json.substring(start, end + 1));
            String intent = text(node, "intent", fallback.intent()).toLowerCase(Locale.ROOT);
            String rewritten = text(node, "rewrittenQuery", fallback.rewrittenQuery());
            return new QueryPlan(
                    intent,
                    rewritten.isBlank() ? fallback.rewrittenQuery() : rewritten,
                    strings(node, "keywords"),
                    strings(node, "pathHints"),
                    strings(node, "languages"),
                    strings(node, "symbolHints")
            );
        } catch (Exception ex) {
            log.warn("Could not parse planner JSON: {}", ex.getMessage());
            return fallback;
        }
    }

    private static QueryPlan merge(QueryPlan planned, QueryPlan fallback) {
        return new QueryPlan(
                planned.intent() == null || planned.intent().isBlank() ? fallback.intent() : planned.intent(),
                planned.rewrittenQuery(),
                distinct(concat(planned.keywords(), fallback.keywords())),
                IdentifierExtractor.sanitizeAll(planned.pathHints()),
                IdentifierExtractor.sanitizeAll(planned.languages()),
                distinct(concat(planned.symbolHints(), fallback.symbolHints()))
        );
    }

    private static String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            return defaultValue;
        }
        return value.asText().trim();
    }

    private static List<String> strings(JsonNode node, String field) {
        JsonNode value = node.get(field);
        List<String> out = new ArrayList<>();
        if (value == null || !value.isArray()) {
            return IdentifierExtractor.sanitizeAll(out);
        }
        value.forEach(item -> {
            if (item != null && item.isTextual()) {
                out.add(item.asText());
            }
        });
        return IdentifierExtractor.sanitizeAll(out);
    }

    private static List<String> concat(List<String> left, List<String> right) {
        List<String> all = new ArrayList<>();
        if (left != null) {
            all.addAll(left);
        }
        if (right != null) {
            all.addAll(right);
        }
        return IdentifierExtractor.sanitizeAll(all);
    }

    private static List<String> distinct(List<String> values) {
        return values.stream().distinct().limit(12).toList();
    }
}
