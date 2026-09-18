package com.genai.gitgpt.user.security;

import com.genai.gitgpt.exception.ErrorMessages;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    public OAuth2AuthenticationFailureHandler() {
        setDefaultFailureUrl("/login?error");
        setAllowSessionCreation(true);
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        String message = ErrorMessages.from(exception);
        request.getSession(true).setAttribute(ErrorMessages.SESSION_KEY, message);
        log.error("GitHub OAuth login failed: {}", message, exception);
        super.onAuthenticationFailure(request, response, exception);
    }
}
