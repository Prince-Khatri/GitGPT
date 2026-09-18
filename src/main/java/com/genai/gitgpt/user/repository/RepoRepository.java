package com.genai.gitgpt.user.repository;

import com.genai.gitgpt.user.models.Repo;
import com.genai.gitgpt.user.models.Users;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepoRepository extends JpaRepository<Repo, UUID> {
    List<Repo> findByUserOrderByFullNameAsc(Users user);

    Optional<Repo> findByUserAndGithubRepoId(Users user, String githubRepoId);

    Optional<Repo> findByRepoIdAndUser(UUID repoId, Users user);

    void deleteByUserAndGithubRepoIdNotIn(Users user, Collection<String> githubRepoIds);

    void deleteByUser(Users user);
}
