package com.genai.gitgpt.rag.controller;

import com.genai.gitgpt.rag.dto.ChatHistoryItem;
import com.genai.gitgpt.rag.service.ChatService;
import com.genai.gitgpt.user.models.Repo;
import com.genai.gitgpt.user.models.Users;
import com.genai.gitgpt.user.security.OAuthAttributes;
import com.genai.gitgpt.user.service.RepoService;
import com.genai.gitgpt.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ChatHistoryController {

    private final UserService userService;
    private final RepoService repoService;
    private final ChatService chatService;

    @GetMapping("/api/chats")
    public List<ChatHistoryItem> all(@AuthenticationPrincipal OAuth2User principal) {
        return chatService.history(currentUser(principal));
    }

    @GetMapping("/api/repos/{repoId}/chats")
    public List<ChatHistoryItem> forRepo(
            @PathVariable UUID repoId,
            @AuthenticationPrincipal OAuth2User principal
    ) {
        Users user = currentUser(principal);
        Repo repo = repoService.requireOwned(user, repoId);
        return chatService.history(user, repo);
    }

    private Users currentUser(OAuth2User principal) {
        return userService.requireByGithubId(OAuthAttributes.asString(principal, "id"));
    }
}
