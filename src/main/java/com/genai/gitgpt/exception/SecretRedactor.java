package com.genai.gitgpt.exception;

import java.util.regex.Pattern;

public final class SecretRedactor {

    private static final Pattern SECRETS = Pattern.compile(
            "(?i)(?:gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,}"
                    + "|Bearer\\s+[A-Za-z0-9._\\-]{20,}|enc:v1:[A-Za-z0-9+/=]+)"
    );

    private SecretRedactor() {
    }

    public static String redact(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return SECRETS.matcher(text).replaceAll("[redacted]");
    }
}
