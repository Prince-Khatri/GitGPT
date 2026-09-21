package com.genai.gitgpt.rag.repository;

import com.genai.gitgpt.rag.model.ChatSession;
import com.genai.gitgpt.user.models.Repo;
import com.genai.gitgpt.user.models.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    Optional<ChatSession> findFirstByUserAndRepoAndCommitShaOrderByUpdatedAtDesc(
            Users user,
            Repo repo,
            String commitSha
    );

    @Query("""
            select s from ChatSession s
            join fetch s.repo
            where s.sessionId = :sessionId and s.user = :user
            """)
    Optional<ChatSession> findBySessionIdAndUser(@Param("sessionId") UUID sessionId, @Param("user") Users user);

    @Query("""
            select s from ChatSession s
            join fetch s.repo
            where s.user = :user
            and exists (select m from ChatMessage m where m.session = s)
            order by s.updatedAt desc
            """)
    List<ChatSession> findHistoryByUser(@Param("user") Users user);

    @Query("""
            select s from ChatSession s
            join fetch s.repo
            where s.user = :user and s.repo = :repo
            and exists (select m from ChatMessage m where m.session = s)
            order by s.updatedAt desc
            """)
    List<ChatSession> findHistoryByUserAndRepo(@Param("user") Users user, @Param("repo") Repo repo);
}
