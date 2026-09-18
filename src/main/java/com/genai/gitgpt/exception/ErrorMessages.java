package com.genai.gitgpt.exception;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.util.StringUtils;

public final class ErrorMessages {

    public static final String SESSION_KEY = "appErrorMessage";

    private ErrorMessages() {
    }

    public static String from(Throwable ex) {
        if (ex == null) {
            return unidentified(null);
        }
        if (ex instanceof AppException) {
            return firstNonBlank(ex.getMessage(), unidentified(ex));
        }
        if (ex instanceof OAuth2AuthenticationException oauthException) {
            String description = oauthException.getError() != null
                    ? oauthException.getError().getDescription()
                    : null;
            return firstNonBlank(description, ex.getMessage(), unidentified(ex));
        }
        return unidentified(ex);
    }

    public static String unidentified(Throwable ex) {
        if (ex == null) {
            return "An unexpected error occurred, and it could not be identified.";
        }
        String type = ex.getClass().getSimpleName();
        String message = firstNonBlank(ex.getMessage(), "no message was provided");
        Throwable root = rootCause(ex);
        if (root != null && root != ex && StringUtils.hasText(root.getMessage())
                && !message.contains(root.getMessage())) {
            return "Unidentified error (" + type + "): " + message
                    + " — caused by " + root.getClass().getSimpleName() + ": " + root.getMessage();
        }
        return "Unidentified error (" + type + "): " + message;
    }

    public static boolean isIdentified(Throwable ex) {
        return ex instanceof AppException
                || (ex instanceof OAuth2AuthenticationException oauth
                && oauth.getError() != null
                && StringUtils.hasText(oauth.getError().getDescription()));
    }

    public static String exceptionType(Throwable ex) {
        return ex == null ? "Unknown" : ex.getClass().getSimpleName();
    }

    private static Throwable rootCause(Throwable ex) {
        Throwable current = ex;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
