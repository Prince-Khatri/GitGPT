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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class RepoController {

    private final UserService userService;
    private final RepoService repoService;

    @GetMapping("/api/repos")
    public List<RepoResponse> cached(@AuthenticationPrincipal OAuth2User principal) {
        return repoService.toResponses(repoService.listCached(currentUser(principal)));
    }

    @GetMapping("/api/repos/{repoId}")
    public RepoResponse one(
            @PathVariable UUID repoId,
            @AuthenticationPrincipal OAuth2User principal
    ) {
        return repoService.toResponse(repoService.requireOwned(currentUser(principal), repoId));
    }

    @GetMapping("/api/repos/github")
    public List<RepoResponse> githubPage(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @AuthenticationPrincipal OAuth2User principal
    ) {
        return repoService.toResponses(repoService.importGithubPage(currentUser(principal), page));
    }

    private Users currentUser(OAuth2User principal) {
        String githubId = OAuthAttributes.asString(principal, "id");
        return userService.findByGithubId(githubId)
                .orElseThrow(() -> new AppException("No local user for GitHub id " + githubId));
    }
}
