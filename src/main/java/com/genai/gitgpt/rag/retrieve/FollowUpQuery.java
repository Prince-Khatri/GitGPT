package com.genai.gitgpt.rag.retrieve;

import java.util.List;

public final class FollowUpQuery {

    private FollowUpQuery() {
    }

    public static String forRetrieval(List<String> priorUserQuestions, String current) {
        String trimmed = current == null ? "" : current.trim();
        if (priorUserQuestions == null || priorUserQuestions.isEmpty()) {
            return trimmed;
        }
        if (QueryPlanner.hasStrongIdentifiers(trimmed)) {
            return trimmed;
        }
        String previous = priorUserQuestions.get(priorUserQuestions.size() - 1);
        if (previous == null || previous.isBlank()) {
            return trimmed;
        }
        return previous.trim() + "\n" + trimmed;
    }
}
