package com.genai.gitgpt.rag.dto;

import com.genai.gitgpt.user.models.IndexStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record IndexJobResponse(
        UUID jobId,
        UUID repoId,
        IndexStatus status,
        String commitSha,
        Integer fileCount,
        Integer chunkCount,
        String progressStep,
        Integer progressPercent,
        boolean cancelRequested,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
