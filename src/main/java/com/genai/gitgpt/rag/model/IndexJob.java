package com.genai.gitgpt.rag.model;

import com.genai.gitgpt.user.models.IndexStatus;
import com.genai.gitgpt.user.models.Repo;
import com.genai.gitgpt.user.models.Users;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "index_jobs")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class IndexJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID jobId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repo_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Repo repo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IndexStatus status;

    private String commitSha;
    private Integer fileCount;
    private Integer chunkCount;
    private String progressStep;
    private Integer progressPercent;
    @Builder.Default
    private boolean cancelRequested = false;

    @Column(columnDefinition = "text")
    private String errorMessage;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
