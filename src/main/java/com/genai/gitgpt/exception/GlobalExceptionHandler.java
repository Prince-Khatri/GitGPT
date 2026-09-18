package com.genai.gitgpt.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitException.class)
    public Object handleRateLimit(RateLimitException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.TOO_MANY_REQUESTS, "Rate limit", ex, true);
    }

    @ExceptionHandler(AppException.class)
    public Object handleAppException(AppException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.BAD_REQUEST, "Application error", ex, true);
    }

    @ExceptionHandler(OAuth2AuthenticationException.class)
    public Object handleOAuthException(OAuth2AuthenticationException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.UNAUTHORIZED, "GitHub login error", ex, true);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        AppException identified = new AppException("You do not have access to this resource.", ex);
        return respond(request, HttpStatus.FORBIDDEN, "Access denied", identified, true);
    }

    @ExceptionHandler(Exception.class)
    public Object handleUnidentified(Exception ex, HttpServletRequest request) {
        return respond(request, HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", ex, false);
    }

    private Object respond(
            HttpServletRequest request,
            HttpStatus status,
            String title,
            Throwable ex,
            boolean identified
    ) {
        String message = identified ? ErrorMessages.from(ex) : ErrorMessages.unidentified(ex);
        if (identified) {
            log.error("{}: {}", title, message, ex);
        } else {
            log.error("Unidentified error on {}: {}", request.getRequestURI(), message, ex);
        }

        ErrorResponse body = new ErrorResponse(
                status.value(),
                title,
                message,
                ErrorMessages.exceptionType(ex),
                identified,
                request.getRequestURI()
        );
        if (isApiRequest(request)) {
            return ResponseEntity.status(status).body(body);
        }
        return html(status, body);
    }

    private ModelAndView html(HttpStatus status, ErrorResponse body) {
        ModelAndView view = new ModelAndView("error");
        view.setStatus(status);
        view.addObject("status", body.status());
        view.addObject("error", body.title());
        view.addObject("message", body.message());
        view.addObject("exceptionType", body.exceptionType());
        view.addObject("identified", body.identified());
        view.addObject("path", body.path());
        return view;
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return request.getRequestURI().startsWith("/api/")
                || (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE)
                && !accept.contains(MediaType.TEXT_HTML_VALUE));
    }
}
