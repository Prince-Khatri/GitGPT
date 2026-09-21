package com.genai.gitgpt.user.service;

import com.genai.gitgpt.user.dto.RepoResponse;
import com.genai.gitgpt.exception.AppException;
import com.genai.gitgpt.user.models.IndexStatus;
import com.genai.gitgpt.user.models.Repo;
import com.genai.gitgpt.user.models.Users;
import com.genai.gitgpt.user.repository.RepoRepository;
import com.genai.gitgpt.user.security.GitHubTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class RepoService {

    private static final String REPOS_URL =
            "https://api.github.com/user/repos?per_page=30&sort=updated&affiliation=owner,collaborator,organization_member";
    private static final ParameterizedTypeReference<List<Map<String, Object>>> REPOS_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RepoRepository repoRepository;
    private final GitHubTokenService gitHubTokenService;
    private final RestClient restClient = RestClient.create();

    public RepoService(RepoRepository repoRepository, GitHubTokenService gitHubTokenService) {
        this.repoRepository = repoRepository;
        this.gitHubTokenService = gitHubTokenService;
    }

    @Transactional(readOnly = true)
    public List<Repo> listCached(Users user) {
        if (user == null || user.getUserID() == null) {
            throw new AppException("A saved GitHub user is required before repositories can be loaded.");
        }
        return repoRepository.findByUserOrderByFullNameAsc(user);
    }

    @Transactional
    public List<Repo> importGithubPage(Users user, int page) {
        if (user == null || user.getUserID() == null) {
            throw new AppException("A saved GitHub user is required before repositories can be loaded.");
        }
        String accessToken = gitHubTokenService.requirePlaintext(user);
        if (!hasText(accessToken)) {
            throw new AppException("No GitHub access token is stored for this user, so repositories cannot be fetched.");
        }
        int safePage = Math.max(1, page);
        List<Map<String, Object>> remoteRepos = fetchRepoPage(accessToken, safePage);
        List<Repo> imported = new ArrayList<>();
        for (Map<String, Object> payload : remoteRepos) {
            Repo saved = upsert(user, payload);
            if (saved != null) {
                imported.add(saved);
            }
        }
        return imported;
    }

    public Repo requireOwned(Users user, UUID repoId) {
        return repoRepository.findByRepoIdAndUser(repoId, user)
                .orElseThrow(() -> new AppException("That repository was not found for this GitHub account."));
    }

    public List<RepoResponse> toResponses(List<Repo> repos) {
        return repos.stream().map(this::toResponse).toList();
    }

    public RepoResponse toResponse(Repo repo) {
        return new RepoResponse(
                repo.getRepoId(),
                repo.getGithubRepoId(),
                repo.getName(),
                repo.getFullName(),
                repo.getDescription(),
                repo.getHtmlUrl(),
                repo.getCloneUrl(),
                repo.getDefaultBranch(),
                repo.getLanguage(),
                repo.getOwnerLogin(),
                repo.isPrivateRepo(),
                repo.getStarCount(),
                repo.getForkCount(),
                repo.getGithubPushedAt(),
                repo.getIndexStatus() == null ? IndexStatus.NOT_INDEXED : repo.getIndexStatus(),
                repo.getIndexedSha(),
                repo.getIndexError(),
                repo.getIndexFileCount(),
                repo.getIndexChunkCount(),
                repo.getIndexEmbeddingModel(),
                repo.getIndexedAt()
        );
    }

    private Repo upsert(Users user, Map<String, Object> payload) {
        String githubRepoId = mapString(payload, "id");
        String name = mapString(payload, "name");
        String fullName = mapString(payload, "full_name");
        if (!hasText(githubRepoId) || !hasText(name) || !hasText(fullName)) {
            log.warn("Skipping GitHub repository payload that is missing id, name, or full_name");
            return null;
        }

        Repo repo = repoRepository.findByUserAndGithubRepoId(user, githubRepoId)
                .orElseGet(() ->                 Repo.builder()
                        .user(user)
                        .githubRepoId(githubRepoId)
                        .name(name)
                        .fullName(fullName)
                        .indexStatus(IndexStatus.NOT_INDEXED)
                        .build());

        repo.setName(name);
        repo.setFullName(fullName);
        repo.setDescription(mapString(payload, "description"));
        repo.setHtmlUrl(mapString(payload, "html_url"));
        repo.setCloneUrl(mapString(payload, "clone_url"));
        repo.setDefaultBranch(mapString(payload, "default_branch"));
        repo.setLanguage(mapString(payload, "language"));
        repo.setOwnerLogin(ownerLogin(payload));
        repo.setPrivateRepo(Boolean.TRUE.equals(payload.get("private")));
        repo.setStarCount(mapInt(payload, "stargazers_count"));
        repo.setForkCount(mapInt(payload, "forks_count"));
        repo.setGithubPushedAt(mapTime(payload, "pushed_at"));
        return repoRepository.save(repo);
    }

    private List<Map<String, Object>> fetchRepoPage(String accessToken, int page) {
        String url = REPOS_URL + "&page=" + page;
        try {
            ResponseEntity<List<Map<String, Object>>> response = restClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .toEntity(REPOS_TYPE);
            return response.getBody() == null ? List.of() : response.getBody();
        } catch (Exception ex) {
            throw new AppException("Failed to fetch GitHub repositories: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    private String ownerLogin(Map<String, Object> payload) {
        Object owner = payload.get("owner");
        if (owner instanceof Map<?, ?> ownerMap) {
            return mapString((Map<String, Object>) ownerMap, "login");
        }
        return null;
    }

    private Integer mapInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDateTime mapTime(Map<String, Object> map, String key) {
        String text = mapString(map, key);
        if (text == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toLocalDateTime();
        } catch (Exception ex) {
            return null;
        }
    }

    private String mapString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() || "null".equals(text) ? null : text;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
