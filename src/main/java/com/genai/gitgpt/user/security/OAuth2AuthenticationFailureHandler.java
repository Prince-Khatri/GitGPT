package com.genai.gitgpt.user.security;

import com.genai.gitgpt.exception.ErrorMessages;
import com.genai.gitgpt.user.config.UiProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final UiProperties uiProperties;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        String message = ErrorMessages.from(exception);
        log.error("GitHub OAuth login failed: {}", message, exception);
        String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8);
        response.sendRedirect(uiProperties.frontendUrl("/?error=" + encoded));
    }
}
