package com.genai.gitgpt.rag.controller;

import com.genai.gitgpt.rag.dto.IndexJobResponse;
import com.genai.gitgpt.rag.service.RepoIndexService;
import com.genai.gitgpt.user.models.Users;
import com.genai.gitgpt.user.security.OAuthAttributes;
import com.genai.gitgpt.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class RepoIndexController {

    private final UserService userService;
    private final RepoIndexService repoIndexService;

    @PostMapping("/api/repos/{repoId}/index")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public IndexJobResponse startFromApi(
            @PathVariable UUID repoId,
            @AuthenticationPrincipal OAuth2User principal
    ) {
        return repoIndexService.enqueue(currentUser(principal), repoId);
    }

    @GetMapping("/api/repos/{repoId}/index")
    public IndexJobResponse status(
            @PathVariable UUID repoId,
            @AuthenticationPrincipal OAuth2User principal
    ) {
        return repoIndexService.status(currentUser(principal), repoId);
    }

    @PostMapping("/api/repos/{repoId}/index/cancel")
    public IndexJobResponse cancel(
            @PathVariable UUID repoId,
            @AuthenticationPrincipal OAuth2User principal
    ) {
        return repoIndexService.cancel(currentUser(principal), repoId);
    }

    private Users currentUser(OAuth2User principal) {
        return userService.requireByGithubId(OAuthAttributes.asString(principal, "id"));
    }
}
