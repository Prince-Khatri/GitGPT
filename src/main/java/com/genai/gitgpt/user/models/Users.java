package com.genai.gitgpt.user.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@ToString(exclude = {"accessToken", "geminiApiKey"})
public class Users {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID userID;

    @Column(unique = true, nullable = false)
    private String githubId;

    @Column(unique = true, nullable = false)
    private String email;

    private String githubUsername;
    private String urlAvatar;

    /** AES-GCM ciphertext (`enc:v2:...`). Decrypt only in memory for GitHub calls. */
    @JsonIgnore
    @Column(columnDefinition = "text")
    private String accessToken;

    /** AES-GCM ciphertext (`enc:v2:...`). Decrypt only in memory for Gemini calls. */
    @JsonIgnore
    @Column(columnDefinition = "text")
    private String geminiApiKey;

    /** Last four characters only. Never the full key. */
    private String geminiApiKeyHint;

    private String chatModel;
    private String embeddingModel;

    private String tokenScope;

    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;

}
