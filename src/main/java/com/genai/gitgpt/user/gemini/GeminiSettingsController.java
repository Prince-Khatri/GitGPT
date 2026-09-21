package com.genai.gitgpt.user.gemini;

import com.genai.gitgpt.user.models.Users;
import com.genai.gitgpt.user.security.OAuthAttributes;
import com.genai.gitgpt.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GeminiSettingsController {

    private final UserService userService;
    private final GeminiSettingsService geminiSettingsService;

    @GetMapping("/api/me/gemini")
    public GeminiSettingsResponse get(@AuthenticationPrincipal OAuth2User principal) {
        return geminiSettingsService.current(currentUser(principal));
    }

    @PutMapping("/api/me/gemini")
    public GeminiSettingsResponse update(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody GeminiSettingsRequest request
    ) {
        return geminiSettingsService.update(currentUser(principal), request);
    }

    @DeleteMapping("/api/me/gemini")
    public GeminiSettingsResponse clear(@AuthenticationPrincipal OAuth2User principal) {
        return geminiSettingsService.clearKey(currentUser(principal));
    }

    private Users currentUser(OAuth2User principal) {
        return userService.requireByGithubId(OAuthAttributes.asString(principal, "id"));
    }
}
