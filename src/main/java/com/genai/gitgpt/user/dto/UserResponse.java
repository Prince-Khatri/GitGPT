package com.genai.gitgpt.user.dto;

import java.util.UUID;

public record UserResponse(
        UUID userId,
        String githubId,
        String email,
        String githubUsername,
        String urlAvatar
) {
}
