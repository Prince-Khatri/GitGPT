package com.genai.gitgpt.user.security;

import com.genai.gitgpt.exception.RateLimitException;
import com.genai.gitgpt.user.config.SecurityProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private final SecurityProperties properties;
    private final ConcurrentHashMap<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    public RateLimitService(SecurityProperties properties) {
        this.properties = properties;
    }

    public void checkAsk(UUID userId) {
        check(
                "ask:" + userId,
                properties.getAskRateLimit(),
                properties.getAskRateWindowSeconds() * 1000L,
                "Too many questions. Wait a few minutes and try again."
        );
    }

    public void checkIndex(UUID userId) {
        check(
                "index:" + userId,
                properties.getIndexRateLimit(),
                properties.getIndexRateWindowSeconds() * 1000L,
                "Too many index requests. Wait a few minutes and try again."
        );
    }

    void check(String key, int limit, long windowMillis, String message) {
        long now = System.currentTimeMillis();
        Deque<Long> hits = windows.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (hits) {
            while (!hits.isEmpty() && hits.peekFirst() < now - windowMillis) {
                hits.pollFirst();
            }
            if (hits.size() >= Math.max(1, limit)) {
                throw new RateLimitException(message);
            }
            hits.addLast(now);
        }
    }
}
