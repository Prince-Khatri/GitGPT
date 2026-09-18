package com.genai.gitgpt.user.service;

import com.genai.gitgpt.user.dto.RepoResponse;
import com.genai.gitgpt.exception.AppException;
import com.genai.gitgpt.user.models.IndexStatus;
import com.genai.gitgpt.user.models.Repo;
import com.genai.gitgpt.user.models.Users;
import com.genai.gitgpt.user.repository.RepoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RepoService {

    private static final String REPOS_URL =
            "https://api.github.com/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator,organization_member";
    private static final ParameterizedTypeReference<List<Map<String, Object>>> REPOS_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RepoRepository repoRepository;
    private final RestClient restClient = RestClient.create();

    public RepoService(RepoRepository repoRepository) {
        this.repoRepository = repoRepository;
    }

    @Transactional
    public List<Repo> syncAndList(Users user) {
        if (user == null || user.getUserID() == null) {
            throw new AppException("A saved GitHub user is required before repositories can be loaded.");
        }
        if (!hasText(user.getAccessToken())) {
            throw new AppException("No GitHub access token is stored for this user, so repositories cannot be fetched.");
        }

        List<Map<String, Object>> remoteRepos = fetchAllRepos(user.getAccessToken());
        Set<String> seenIds = remoteRepos.stream()
                .map(payload -> mapString(payload, "id"))
                .filter(this::hasText)
                .collect(Collectors.toSet());

        for (Map<String, Object> payload : remoteRepos) {
            upsert(user, payload);
        }

        if (seenIds.isEmpty()) {
            repoRepository.deleteByUser(user);
        } else {
            repoRepository.deleteByUserAndGithubRepoIdNotIn(user, seenIds);
        }

        return repoRepository.findByUserOrderByFullNameAsc(user);
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
                repo.getIndexStatus() == null ? IndexStatus.NOT_INDEXED : repo.getIndexStatus(),
                repo.getIndexedSha(),
                repo.getIndexError(),
                repo.getIndexFileCount(),
                repo.getIndexChunkCount(),
                repo.getIndexedAt()
        );
    }

    private void upsert(Users user, Map<String, Object> payload) {
        String githubRepoId = mapString(payload, "id");
        String name = mapString(payload, "name");
        String fullName = mapString(payload, "full_name");
        if (!hasText(githubRepoId) || !hasText(name) || !hasText(fullName)) {
            log.warn("Skipping GitHub repository payload that is missing id, name, or full_name");
            return;
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
        repoRepository.save(repo);
    }

    private List<Map<String, Object>> fetchAllRepos(String accessToken) {
        List<Map<String, Object>> all = new ArrayList<>();
        String url = REPOS_URL;
        try {
            while (url != null) {
                ResponseEntity<List<Map<String, Object>>> response = restClient.get()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .retrieve()
                        .toEntity(REPOS_TYPE);
                if (response.getBody() != null) {
                    all.addAll(response.getBody());
                }
                url = nextPageUrl(response.getHeaders().getFirst(HttpHeaders.LINK));
            }
        } catch (Exception ex) {
            throw new AppException("Failed to fetch GitHub repositories: " + ex.getMessage(), ex);
        }
        return all;
    }

    private String nextPageUrl(String linkHeader) {
        if (!hasText(linkHeader)) {
            return null;
        }
        for (String part : linkHeader.split(",")) {
            String[] sections = part.split(";");
            if (sections.length < 2) {
                continue;
            }
            if (sections[1].contains("rel=\"next\"")) {
                String url = sections[0].trim();
                if (url.startsWith("<") && url.endsWith(">")) {
                    return url.substring(1, url.length() - 1);
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String ownerLogin(Map<String, Object> payload) {
        Object owner = payload.get("owner");
        if (owner instanceof Map<?, ?> ownerMap) {
            return mapString((Map<String, Object>) ownerMap, "login");
        }
        return null;
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
