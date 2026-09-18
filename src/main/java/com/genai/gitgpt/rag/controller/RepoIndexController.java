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
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class RepoIndexController {

    private final UserService userService;
    private final RepoIndexService repoIndexService;

    @PostMapping("/repos/{repoId}/index")
    public String startFromPage(
            @PathVariable UUID repoId,
            @AuthenticationPrincipal OAuth2User principal
    ) {
        repoIndexService.enqueue(currentUser(principal), repoId);
        return "redirect:/home";
    }

    @PostMapping("/api/repos/{repoId}/index")
    @ResponseBody
    @ResponseStatus(HttpStatus.ACCEPTED)
    public IndexJobResponse startFromApi(
            @PathVariable UUID repoId,
            @AuthenticationPrincipal OAuth2User principal
    ) {
        return repoIndexService.enqueue(currentUser(principal), repoId);
    }

    @GetMapping("/api/repos/{repoId}/index")
    @ResponseBody
    public IndexJobResponse status(
            @PathVariable UUID repoId,
            @AuthenticationPrincipal OAuth2User principal
    ) {
        return repoIndexService.status(currentUser(principal), repoId);
    }

    private Users currentUser(OAuth2User principal) {
        return userService.requireByGithubId(OAuthAttributes.asString(principal, "id"));
    }
}
