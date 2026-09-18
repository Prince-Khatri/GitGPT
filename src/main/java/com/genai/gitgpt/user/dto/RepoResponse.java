package com.genai.gitgpt.user.dto;

import com.genai.gitgpt.user.models.IndexStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record RepoResponse(
        UUID repoId,
        String githubRepoId,
        String name,
        String fullName,
        String description,
        String htmlUrl,
        String cloneUrl,
        String defaultBranch,
        String language,
        String ownerLogin,
        boolean privateRepo,
        IndexStatus indexStatus,
        String indexedSha,
        String indexError,
        Integer indexFileCount,
        Integer indexChunkCount,
        LocalDateTime indexedAt
) {
}
