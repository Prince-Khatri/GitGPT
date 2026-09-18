package com.genai.gitgpt.rag.repository;

import com.genai.gitgpt.rag.model.IndexJob;
import com.genai.gitgpt.user.models.IndexStatus;
import com.genai.gitgpt.user.models.Repo;
import com.genai.gitgpt.user.models.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface IndexJobRepository extends JpaRepository<IndexJob, UUID> {

    Optional<IndexJob> findTopByRepoOrderByCreatedAtDesc(Repo repo);

    boolean existsByRepoAndStatusIn(Repo repo, Collection<IndexStatus> statuses);

    boolean existsByUserAndStatusIn(Users user, Collection<IndexStatus> statuses);

    @Query("""
            select j from IndexJob j
            join fetch j.repo r
            join fetch r.user
            where j.jobId = :jobId
            """)
    Optional<IndexJob> findWithRepoAndUserById(@Param("jobId") UUID jobId);
}
