package com.genai.gitgpt.rag.retrieve;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IdentifierExtractor {

    private static final Pattern BACKTICK = Pattern.compile("`([^`]+)`");
    private static final Pattern CAMEL = Pattern.compile("\\b[A-Z][a-zA-Z0-9]+(?:[A-Z][a-zA-Z0-9]+)+\\b");
    private static final Pattern MIXED = Pattern.compile("\\b[a-z]+[A-Z][a-zA-Z0-9]*\\b");
    private static final Pattern PATH = Pattern.compile("\\b[\\w./-]+\\.(?:java|kt|py|js|ts|tsx|jsx|md|xml|yml|yaml|properties|gradle)\\b");

    private IdentifierExtractor() {
    }

    public static List<String> extract(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        Set<String> found = new LinkedHashSet<>();
        addAll(found, BACKTICK.matcher(question));
        addAll(found, PATH.matcher(question));
        addAll(found, CAMEL.matcher(question));
        addAll(found, MIXED.matcher(question));
        return new ArrayList<>(found);
    }

    public static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("%", "").replace("_", " ").replace("\\", " ").trim();
    }

    public static List<String> sanitizeAll(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> clean = new ArrayList<>();
        for (String value : values) {
            String sanitized = sanitize(value);
            if (!sanitized.isBlank() && sanitized.length() <= 80) {
                clean.add(sanitized);
            }
        }
        return clean;
    }

    private static void addAll(Set<String> found, Matcher matcher) {
        while (matcher.find()) {
            String value = matcher.groupCount() >= 1 && matcher.group(1) != null ? matcher.group(1) : matcher.group();
            if (value != null && value.length() >= 2) {
                found.add(value.trim());
            }
        }
    }

    public static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
