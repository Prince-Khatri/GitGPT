package com.genai.gitgpt.user.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "repos",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "github_repo_id"})
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class Repo {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID repoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "github_repo_id", nullable = false)
    private String githubRepoId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String fullName;

    @Column(columnDefinition = "text")
    private String description;

    private String htmlUrl;
    private String cloneUrl;
    private String defaultBranch;
    private String language;
    private String ownerLogin;

    @Column(name = "is_private")
    private boolean privateRepo;

    private Integer starCount;
    private Integer forkCount;
    private LocalDateTime githubPushedAt;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private IndexStatus indexStatus = IndexStatus.NOT_INDEXED;

    private String indexedSha;

    @Column(columnDefinition = "text")
    private String indexError;

    private Integer indexFileCount;
    private Integer indexChunkCount;
    private String indexEmbeddingModel;
    private LocalDateTime indexedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
