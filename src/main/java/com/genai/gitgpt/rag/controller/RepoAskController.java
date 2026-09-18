package com.genai.gitgpt.rag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genai.gitgpt.exception.AppException;
import com.genai.gitgpt.rag.dto.AskResponse;
import com.genai.gitgpt.rag.dto.ChatMessageResponse;
import com.genai.gitgpt.rag.dto.CitationResponse;
import com.genai.gitgpt.rag.model.ChatSession;
import com.genai.gitgpt.rag.service.ChatService;
import com.genai.gitgpt.rag.service.RepoAskService;
import com.genai.gitgpt.user.models.IndexStatus;
import com.genai.gitgpt.user.models.Repo;
import com.genai.gitgpt.user.models.Users;
import com.genai.gitgpt.user.security.OAuthAttributes;
import com.genai.gitgpt.user.service.RepoService;
import com.genai.gitgpt.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

@Controller
@Slf4j
public class RepoAskController {

    private final UserService userService;
    private final RepoService repoService;
    private final RepoAskService repoAskService;
    private final ChatService chatService;
    private final Executor retrieveExecutor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RepoAskController(
            UserService userService,
            RepoService repoService,
            RepoAskService repoAskService,
            ChatService chatService,
            @Qualifier("retrieveExecutor") Executor retrieveExecutor
    ) {
        this.userService = userService;
        this.repoService = repoService;
        this.repoAskService = repoAskService;
        this.chatService = chatService;
        this.retrieveExecutor = retrieveExecutor;
    }

    @GetMapping("/repos/{repoId}")
    public String askPage(
            @PathVariable UUID repoId,
            @RequestParam(value = "session", required = false) UUID sessionId,
            @AuthenticationPrincipal OAuth2User principal,
            Model model
    ) {
        Users user = currentUser(principal);
        Repo repo = requireReady(user, repoId);
        ChatSession session = chatService.open(user, repo, sessionId);
        model.addAttribute("user", user);
        model.addAttribute("repo", repo);
        model.addAttribute("session", session);
        model.addAttribute("messages", chatService.toResponses(session));
        return "ask";
    }

    @PostMapping("/repos/{repoId}/ask")
    public String askFromPage(
            @PathVariable UUID repoId,
            @RequestParam("question") String question,
            @RequestParam(value = "sessionId", required = false) UUID sessionId,
            @AuthenticationPrincipal OAuth2User principal
    ) {
        Users user = currentUser(principal);
        requireReady(user, repoId);
        AskResponse answer = repoAskService.ask(user, repoId, question, sessionId);
        return "redirect:/repos/" + repoId + "?session=" + answer.sessionId();
    }

    @PostMapping("/repos/{repoId}/chat/new")
    public String newChat(
            @PathVariable UUID repoId,
            @AuthenticationPrincipal OAuth2User principal
    ) {
        Users user = currentUser(principal);
        Repo repo = requireReady(user, repoId);
        ChatSession session = chatService.startNew(user, repo);
        return "redirect:/repos/" + repoId + "?session=" + session.getSessionId();
    }

    @PostMapping(value = "/repos/{repoId}/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamFromPage(
            @PathVariable UUID repoId,
            @RequestParam("question") String question,
            @RequestParam(value = "sessionId", required = false) UUID sessionId,
            @AuthenticationPrincipal OAuth2User principal
    ) {
        Users user = currentUser(principal);
        requireReady(user, repoId);
        SseEmitter emitter = new SseEmitter(120_000L);
        retrieveExecutor.execute(() -> {
            try {
                RepoAskService.PreparedAsk prepared = repoAskService.prepare(user, repoId, question, sessionId);
                AskResponse meta = repoAskService.toResponse(prepared, "");
                send(emitter, "meta", objectMapper.writeValueAsString(Map.of(
                        "sessionId", meta.sessionId().toString(),
                        "intent", nullToEmpty(meta.intent()),
                        "rewrittenQuery", nullToEmpty(meta.rewrittenQuery()),
                        "grounded", meta.grounded(),
                        "citations", citations(meta.citations())
                )));
                repoAskService.streamAnswer(prepared, delta -> {
                    try {
                        send(emitter, "token", objectMapper.writeValueAsString(delta));
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                });
                send(emitter, "done", "\"ok\"");
                emitter.complete();
            } catch (Exception ex) {
                log.error("Streaming ask failed: {}", ex.getMessage(), ex);
                try {
                    String message = ex instanceof AppException ? ex.getMessage() : "Ask failed.";
                    send(emitter, "error", objectMapper.writeValueAsString(message));
                } catch (Exception ignored) {
                    // emitter already dead
                }
                emitter.complete();
            }
        });
        return emitter;
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
        UUID sessionId = parseUuid(body == null ? null : body.get("sessionId"));
        return repoAskService.ask(user, repoId, question, sessionId);
    }

    @GetMapping("/api/repos/{repoId}/chat")
    @ResponseBody
    public Map<String, Object> chatFromApi(
            @PathVariable UUID repoId,
            @RequestParam(value = "sessionId", required = false) UUID sessionId,
            @AuthenticationPrincipal OAuth2User principal
    ) {
        Users user = currentUser(principal);
        Repo repo = requireReady(user, repoId);
        ChatSession session = chatService.open(user, repo, sessionId);
        List<ChatMessageResponse> messages = chatService.toResponses(session);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", session.getSessionId());
        payload.put("commitSha", session.getCommitSha());
        payload.put("messages", messages);
        return payload;
    }

    private void send(SseEmitter emitter, String name, String json) throws Exception {
        emitter.send(SseEmitter.event().name(name).data(json, MediaType.APPLICATION_JSON));
    }

    private static List<Map<String, Object>> citations(List<CitationResponse> citations) {
        if (citations == null) {
            return List.of();
        }
        return citations.stream().map(citation -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("path", citation.path());
            row.put("startLine", citation.startLine());
            row.put("endLine", citation.endLine());
            row.put("commitSha", citation.commitSha());
            row.put("source", citation.source());
            return row;
        }).toList();
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

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
