package com.genai.gitgpt.user.security;

import com.genai.gitgpt.user.config.UiProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UiProperties uiProperties;
    private final LoginTicketService loginTicketService;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        String home = uiProperties.frontendUrl("/home");
        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User principal) {
            String githubId = OAuthAttributes.asString(principal, "id");
            if (githubId != null && !githubId.isBlank()) {
                home = uiProperties.frontendUrl("/home?ticket=" + loginTicketService.issue(githubId));
            }
        }
        response.sendRedirect(home);
    }
}
