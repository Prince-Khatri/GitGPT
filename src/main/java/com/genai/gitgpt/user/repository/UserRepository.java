package com.genai.gitgpt.user.repository;

import com.genai.gitgpt.user.models.Users;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;


public interface UserRepository extends JpaRepository<Users, UUID> {
    Optional<Users> findByGithubId(String githubId);
    Optional<Users> findByEmail(String email);
    Optional<Users> findByGithubUsername(String githubUsername);
}