package com.genai.gitgpt.rag.controller;

import com.genai.gitgpt.exception.AppException;
import com.genai.gitgpt.rag.dto.AskResponse;
import com.genai.gitgpt.rag.service.RepoAskService;
import com.genai.gitgpt.user.models.IndexStatus;
import com.genai.gitgpt.user.models.Repo;
import com.genai.gitgpt.user.models.Users;
import com.genai.gitgpt.user.security.OAuthAttributes;
import com.genai.gitgpt.user.service.RepoService;
import com.genai.gitgpt.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class RepoAskController {

    private final UserService userService;
    private final RepoService repoService;
    private final RepoAskService repoAskService;

    @GetMapping("/repos/{repoId}")
    public String askPage(
            @PathVariable UUID repoId,
            @AuthenticationPrincipal OAuth2User principal,
            Model model
    ) {
        Users user = currentUser(principal);
        Repo repo = requireReady(user, repoId);
        model.addAttribute("user", user);
        model.addAttribute("repo", repo);
        return "ask";
    }

    @PostMapping("/repos/{repoId}/ask")
    public String askFromPage(
            @PathVariable UUID repoId,
            @RequestParam("question") String question,
            @AuthenticationPrincipal OAuth2User principal,
            Model model
    ) {
        Users user = currentUser(principal);
        Repo repo = requireReady(user, repoId);
        AskResponse answer = repoAskService.ask(user, repoId, question);
        model.addAttribute("user", user);
        model.addAttribute("repo", repo);
        model.addAttribute("result", answer);
        model.addAttribute("question", question);
        return "ask";
    }

    @PostMapping(value = "/api/repos/{repoId}/ask", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public AskResponse askFromApi(
            @PathVariable UUID repoId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal OAuth2User principal
    ) {
        Users user = currentUser(principal);
        requireReady(user, repoId);
        String question = body == null ? null : body.get("question");
        return repoAskService.ask(user, repoId, question);
    }

    private Repo requireReady(Users user, UUID repoId) {
        Repo repo = repoService.requireOwned(user, repoId);
        if (repo.getIndexStatus() != IndexStatus.READY) {
            throw new AppException("Index this repository first. Questions run only against a READY snapshot.");
        }
        return repo;
    }

    private Users currentUser(OAuth2User principal) {
        return userService.requireByGithubId(OAuthAttributes.asString(principal, "id"));
    }
}
