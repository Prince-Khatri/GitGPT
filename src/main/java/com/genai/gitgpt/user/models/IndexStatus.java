package com.genai.gitgpt.user.models;

public enum IndexStatus {
    NOT_INDEXED,
    QUEUED,
    RUNNING,
    READY,
    FAILED;

    public boolean isInProgress() {
        return this == QUEUED || this == RUNNING;
    }

    public boolean inProgress() {
        return isInProgress();
    }

    public String actionLabel() {
        return switch (this) {
            case QUEUED, RUNNING -> "Indexing…";
            case READY -> "Re-index";
            case FAILED -> "Retry index";
            case NOT_INDEXED -> "Index";
        };
    }
}
