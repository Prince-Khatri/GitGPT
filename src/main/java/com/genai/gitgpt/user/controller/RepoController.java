package com.genai.gitgpt.user.controller;

import com.genai.gitgpt.user.dto.RepoResponse;
import com.genai.gitgpt.exception.AppException;
import com.genai.gitgpt.user.models.Users;
import com.genai.gitgpt.user.security.OAuthAttributes;
import com.genai.gitgpt.user.service.RepoService;
import com.genai.gitgpt.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class RepoController {

    private final UserService userService;
    private final RepoService repoService;

    @GetMapping("/api/repos")
    public List<RepoResponse> list(@AuthenticationPrincipal OAuth2User principal) {
        String githubId = OAuthAttributes.asString(principal, "id");
        Users user = userService.findByGithubId(githubId)
                .orElseThrow(() -> new AppException("No local user for GitHub id " + githubId));
        return repoService.toResponses(repoService.syncAndList(user));
    }
}
