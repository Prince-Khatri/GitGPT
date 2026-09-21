package com.genai.gitgpt.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.TOO_MANY_REQUESTS, "Rate limit", ex, true);
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.BAD_REQUEST, "Application error", ex, true);
    }

    @ExceptionHandler(OAuth2AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleOAuthException(
            OAuth2AuthenticationException ex,
            HttpServletRequest request
    ) {
        return respond(request, HttpStatus.UNAUTHORIZED, "GitHub login error", ex, true);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        AppException identified = new AppException("You do not have access to this resource.", ex);
        return respond(request, HttpStatus.FORBIDDEN, "Access denied", identified, true);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnidentified(Exception ex, HttpServletRequest request) {
        return respond(request, HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", ex, false);
    }

    private ResponseEntity<ErrorResponse> respond(
            HttpServletRequest request,
            HttpStatus status,
            String title,
            Throwable ex,
            boolean identified
    ) {
        String message = identified ? ErrorMessages.from(ex) : ErrorMessages.unidentified(ex);
        if (ex instanceof RateLimitException) {
            log.warn("{} on {}: {}", title, request.getRequestURI(), message);
        } else if (identified) {
            log.warn("{} on {}: {}", title, request.getRequestURI(), message);
        } else {
            log.error("Unidentified error on {}: {}", request.getRequestURI(), message, ex);
        }
        return ResponseEntity.status(status).body(new ErrorResponse(
                status.value(),
                title,
                message,
                ErrorMessages.exceptionType(ex),
                identified,
                request.getRequestURI()
        ));
    }
}
