package com.genai.gitgpt.rag.model;

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
@Table(
        name = "chat_sessions",
        indexes = @Index(name = "idx_chat_session_user_repo_sha", columnList = "user_id, repo_id, commit_sha")
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repo_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Repo repo;

    @Column(nullable = false)
    private String commitSha;

    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
