package com.genai.gitgpt.user.controller;

import com.genai.gitgpt.user.dto.UserResponse;
import com.genai.gitgpt.user.gemini.GeminiSettingsService;
import com.genai.gitgpt.user.security.OAuthAttributes;
import com.genai.gitgpt.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final GeminiSettingsService geminiSettingsService;

    @GetMapping("/api/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("token", token.getToken());
        payload.put("headerName", token.getHeaderName());
        payload.put("parameterName", token.getParameterName());
        return payload;
    }

    @GetMapping("/api/me")
    public UserResponse me(@AuthenticationPrincipal OAuth2User principal) {
        String githubId = OAuthAttributes.asString(principal, "id");
        return userService.findByGithubId(githubId)
                .map(geminiSettingsService::toUserResponse)
                .orElseGet(() -> geminiSettingsService.anonymous(
                        githubId,
                        OAuthAttributes.asString(principal, "email"),
                        OAuthAttributes.asString(principal, "login"),
                        OAuthAttributes.asString(principal, "avatar_url")
                ));
    }
}
