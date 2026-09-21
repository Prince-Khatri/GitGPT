package com.genai.gitgpt.exception;

public final class GeminiErrors {

    private GeminiErrors() {
    }

    public static AppException wrap(Throwable ex) {
        if (ex instanceof RateLimitException rate) {
            return rate;
        }
        if (isRateLimit(ex)) {
            return new RateLimitException(
                    "This Gemini model is rate limited right now. Wait a minute and try again, or pick another model in Settings."
            );
        }
        if (ex instanceof AppException app) {
            return app;
        }
        return new AppException(friendly(ex), ex);
    }

    public static boolean isRateLimit(Throwable ex) {
        String text = chain(ex).toLowerCase();
        return text.contains("429")
                || text.contains("resource_exhausted")
                || text.contains("rate limit")
                || text.contains("too many")
                || text.contains("quota")
                || text.contains("resource exhausted");
    }

    public static String userMessage(Throwable ex) {
        return wrap(ex).getMessage();
    }

    private static String friendly(Throwable ex) {
        String text = chain(ex).toLowerCase();
        if (text.contains("timeout") || text.contains("timed out")) {
            return "The model took too long to answer. Try again or pick a faster model in Settings.";
        }
        if (text.contains("404") || text.contains("not found") || text.contains("not supported")) {
            return "This Gemini model is not available for your API key. Pick another model in Settings.";
        }
        return "Ask failed. Try again, or pick another model in Settings.";
    }

    private static String chain(Throwable ex) {
        StringBuilder text = new StringBuilder();
        Throwable current = ex;
        int depth = 0;
        while (current != null && depth++ < 8) {
            if (current.getMessage() != null) {
                text.append(' ').append(current.getMessage());
            }
            text.append(' ').append(current.getClass().getSimpleName());
            current = current.getCause();
        }
        return SecretRedactor.redact(text.toString());
    }
}
