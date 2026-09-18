package com.genai.gitgpt.user.controller;

import com.genai.gitgpt.user.dto.UserResponse;
import com.genai.gitgpt.user.models.Users;
import com.genai.gitgpt.user.security.OAuthAttributes;
import com.genai.gitgpt.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/api/me")
    public UserResponse me(@AuthenticationPrincipal OAuth2User principal) {
        String githubId = OAuthAttributes.asString(principal, "id");
        return userService.findByGithubId(githubId)
                .map(this::toResponse)
                .orElseGet(() -> new UserResponse(
                        null,
                        githubId,
                        OAuthAttributes.asString(principal, "email"),
                        OAuthAttributes.asString(principal, "login"),
                        OAuthAttributes.asString(principal, "avatar_url")
                ));
    }

    private UserResponse toResponse(Users user) {
        return new UserResponse(
                user.getUserID(),
                user.getGithubId(),
                user.getEmail(),
                user.getGithubUsername(),
                user.getUrlAvatar()
        );
    }
}
