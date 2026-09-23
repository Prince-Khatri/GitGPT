package com.genai.gitgpt.user.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * One-time code after GitHub OAuth. The SPA exchanges it for a bearer token so
 * login works when the UI and API are on different hosts (cookies stay on the API host).
 */
@Service
public class LoginTicketService {

    private final Cache<String, String> tickets = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(2))
            .maximumSize(10_000)
            .build();
    private final SecureRandom random = new SecureRandom();

    public String issue(String githubId) {
        if (!StringUtils.hasText(githubId)) {
            throw new IllegalArgumentException("githubId is required");
        }
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        tickets.put(ticket, githubId);
        return ticket;
    }

    public String consume(String ticket) {
        if (!StringUtils.hasText(ticket)) {
            return null;
        }
        return tickets.asMap().remove(ticket.trim());
    }
}
