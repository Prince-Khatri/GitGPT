package com.genai.gitgpt.exception;

public class RateLimitException extends AppException {

    public RateLimitException(String message) {
        super(message);
    }
}
