package com.genai.gitgpt.exception;

public record ErrorResponse(
        int status,
        String title,
        String message,
        String exceptionType,
        boolean identified,
        String path
) {
}
