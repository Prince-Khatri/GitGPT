package com.genai.gitgpt.user.service;

import com.genai.gitgpt.user.models.Users;
import com.genai.gitgpt.user.repository.UserRepository;
import com.genai.gitgpt.exception.AppException;
import com.genai.gitgpt.user.security.GitHubTokenService;
import com.genai.gitgpt.user.security.OAuthAttributes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
public class UserService {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> EMAILS_TYPE =
            new ParameterizedTypeReference<>() {};

    private final UserRepository userRepository;
    private final GitHubTokenService gitHubTokenService;
    private final RestClient restClient = RestClient.create();

    public UserService(UserRepository userRepository, GitHubTokenService gitHubTokenService) {
        this.userRepository = userRepository;
        this.gitHubTokenService = gitHubTokenService;
    }

    @Transactional
    public Users upsertFromOAuth(OAuth2User oauth2User, String accessToken, Set<String> scopes) {
        String githubId = OAuthAttributes.asString(oauth2User, "id");
        String username = OAuthAttributes.asString(oauth2User, "login");
        String avatar = OAuthAttributes.asString(oauth2User, "avatar_url");
        if (githubId == null || username == null) {
            throw new AppException("GitHub profile is missing an id or login, so the user cannot be saved.");
        }
        Users user = userRepository.findByGithubId(githubId)
                .orElseGet(() -> Users.builder()
                        .githubId(githubId)
                        .build());
        String email = resolveEmail(oauth2User, accessToken, githubId, username, scopes, user);
        String tokenScope = scopes == null || scopes.isEmpty() ? null : String.join(",", scopes);

        user.setGithubId(githubId);
        user.setEmail(email);
        user.setGithubUsername(username);
        user.setUrlAvatar(avatar);
        gitHubTokenService.encryptInto(user, accessToken);
        user.setTokenScope(tokenScope);
        try {
            Users saved = userRepository.save(user);
            gitHubTokenService.remember(saved, accessToken);
            return saved;
        } catch (RuntimeException ex) {
            throw new AppException("Failed to save GitHub user: " + ex.getMessage(), ex);
        }
    }

    public Users requireByGithubId(String githubId) {
        return findByGithubId(githubId)
                .orElseThrow(() -> new AppException("No local user for GitHub id " + githubId));
    }

    public Optional<Users> findByGithubId(String githubId) {
        return userRepository.findByGithubId(githubId);
    }

    private String resolveEmail(
            OAuth2User oauth2User,
            String accessToken,
            String githubId,
            String username,
            Set<String> scopes,
            Users existing
    ) {
        String publicEmail = OAuthAttributes.asString(oauth2User, "email");
        if (hasText(publicEmail)) {
            return publicEmail;
        }

        if (hasEmailScope(scopes)) {
            try {
                List<Map<String, Object>> emails = restClient.get()
                        .uri("https://api.github.com/user/emails")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .retrieve()
                        .body(EMAILS_TYPE);

                if (emails != null) {
                    String primaryVerified = pickEmail(emails, true, true);
                    if (hasText(primaryVerified)) {
                        return primaryVerified;
                    }
                    String verified = pickEmail(emails, false, true);
                    if (hasText(verified)) {
                        return verified;
                    }
                    String any = pickEmail(emails, false, false);
                    if (hasText(any)) {
                        return any;
                    }
                }
            } catch (Exception ex) {
                if (isEmailForbidden(ex)) {
                    log.info("GitHub did not share emails for {}. Login continues without that list.", username);
                } else {
                    log.warn("Could not load GitHub emails for user {}: {}", username, ex.getMessage());
                }
            }
        }

        if (existing != null && hasText(existing.getEmail())) {
            return existing.getEmail();
        }
        return githubId + "+" + username + "@users.noreply.github.com";
    }

    private static boolean hasEmailScope(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return true;
        }
        return scopes.stream().anyMatch(scope -> scope != null && scope.contains("user:email"));
    }

    private static boolean isEmailForbidden(Exception ex) {
        String text = String.valueOf(ex.getMessage()).toLowerCase();
        return text.contains("403") || text.contains("forbidden") || text.contains("not accessible by integration");
    }

    private String pickEmail(List<Map<String, Object>> emails, boolean requirePrimary, boolean requireVerified) {
        return emails.stream()
                .filter(email -> !requirePrimary || Boolean.TRUE.equals(email.get("primary")))
                .filter(email -> !requireVerified || Boolean.TRUE.equals(email.get("verified")))
                .map(email -> (String) email.get("email"))
                .filter(this::hasText)
                .findFirst()
                .orElse(null);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
