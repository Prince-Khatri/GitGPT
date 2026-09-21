package com.genai.gitgpt.exception;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;

@RestController
@RequestMapping("/error")
@Slf4j
public class AppErrorController implements ErrorController {

    private final ErrorAttributes errorAttributes;

    public AppErrorController(ErrorAttributes errorAttributes) {
        this.errorAttributes = errorAttributes;
    }

    @RequestMapping
    public ResponseEntity<ErrorResponse> errorJson(HttpServletRequest request) {
        ErrorResponse body = build(request);
        return ResponseEntity.status(body.status()).body(body);
    }

    private ErrorResponse build(HttpServletRequest request) {
        HttpStatus status = resolveStatus(request);
        String path = resolvePath(request);
        Throwable error = resolveException(request);
        boolean identified = ErrorMessages.isIdentified(error);
        String message = message(status, path, error);
        String title = identified ? "Application error" : "Unexpected error";

        if (status == HttpStatus.NOT_FOUND) {
            log.warn("Not found: {}", path);
        } else if (identified) {
            log.error("{} on {}: {}", title, path, message, error);
        } else {
            log.error("Unidentified error on {}: {}", path, message, error);
        }

        return new ErrorResponse(
                status.value(),
                title,
                message,
                ErrorMessages.exceptionType(error),
                identified,
                path
        );
    }

    private String message(HttpStatus status, String path, Throwable error) {
        if (error != null) {
            return ErrorMessages.isIdentified(error)
                    ? ErrorMessages.from(error)
                    : ErrorMessages.unidentified(error);
        }
        if (status == HttpStatus.NOT_FOUND) {
            return "No resource was found for " + path + ".";
        }
        return "An unexpected error occurred, and it could not be identified. HTTP " + status.value();
    }

    private Throwable resolveException(HttpServletRequest request) {
        Throwable error = errorAttributes.getError(new ServletWebRequest(request));
        if (error != null) {
            return error;
        }
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        return attribute instanceof Throwable throwable ? throwable : null;
    }

    private HttpStatus resolveStatus(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (status instanceof Integer code) {
            try {
                return HttpStatus.valueOf(code);
            } catch (Exception ignored) {
                return HttpStatus.INTERNAL_SERVER_ERROR;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String resolvePath(HttpServletRequest request) {
        Object path = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (path instanceof String uri && !uri.isBlank()) {
            return uri;
        }
        String query = request.getQueryString();
        String current = request.getRequestURI();
        return query == null ? current : current + "?" + query;
    }
}
