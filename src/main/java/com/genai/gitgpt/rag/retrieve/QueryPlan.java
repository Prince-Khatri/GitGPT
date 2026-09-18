package com.genai.gitgpt.rag.retrieve;

import java.util.List;

public record QueryPlan(
        String intent,
        String rewrittenQuery,
        List<String> keywords,
        List<String> pathHints,
        List<String> languages,
        List<String> symbolHints
) {
    public static QueryPlan fallback(String question) {
        List<String> identifiers = IdentifierExtractor.extract(question);
        return new QueryPlan(
                "explain",
                question == null ? "" : question.trim(),
                identifiers,
                List.of(),
                List.of(),
                identifiers
        );
    }
}
