package com.genai.gitgpt.user.controller;

import com.genai.gitgpt.exception.AppException;
import com.genai.gitgpt.user.security.LoginTicketService;
import com.genai.gitgpt.user.security.SessionTokenService;
import com.genai.gitgpt.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AuthSessionController {

    private final LoginTicketService loginTicketService;
    private final SessionTokenService sessionTokenService;
    private final UserService userService;

    @PostMapping("/api/auth/session")
    public Map<String, Object> exchange(@RequestBody(required = false) Map<String, String> body) {
        String ticket = body == null ? null : body.get("ticket");
        String githubId = loginTicketService.consume(ticket);
        if (githubId == null || userService.findByGithubId(githubId).isEmpty()) {
            throw new AppException("Sign-in did not complete. Try GitHub again.");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", sessionTokenService.issue(githubId));
        payload.put("expiresInSeconds", sessionTokenService.ttlSeconds());
        return payload;
    }
}
