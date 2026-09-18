package com.genai.gitgpt.rag.ingest;

import com.genai.gitgpt.exception.AppException;
import com.genai.gitgpt.rag.config.IndexProperties;
import com.genai.gitgpt.user.models.Repo;
import com.genai.gitgpt.user.models.Users;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubSnapshotService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final IndexProperties properties;
    private final SourceFileFilter fileFilter;
    private final RestClient restClient = RestClient.create();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public record Snapshot(String commitSha, List<SourceFile> files) {
    }

    public Snapshot fetch(Users user, Repo repo) {
        String token = user.getAccessToken();
        if (token == null || token.isBlank()) {
            throw new AppException("No GitHub access token is stored, so this repository cannot be indexed.");
        }
        String fullName = repo.getFullName();
        if (fullName == null || fullName.isBlank()) {
            throw new AppException("Repository is missing a full name, so it cannot be indexed.");
        }
        String[] ownerRepo = splitFullName(fullName);
        String branch = hasText(repo.getDefaultBranch()) ? repo.getDefaultBranch() : "main";
        String commitSha = resolveCommitSha(ownerRepo[0], ownerRepo[1], branch, token);
        byte[] zip = downloadZipball(ownerRepo[0], ownerRepo[1], commitSha, token);
        List<SourceFile> files = readZip(zip, repo.getRepoId());
        return new Snapshot(commitSha, files);
    }

    private String resolveCommitSha(String owner, String name, String branch, String token) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri("https://api.github.com/repos/{owner}/{repo}/commits/{branch}", owner, name, branch)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header(HttpHeaders.USER_AGENT, "GitGPT")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .body(MAP_TYPE);
            Object sha = body == null ? null : body.get("sha");
            if (sha == null || String.valueOf(sha).isBlank()) {
                throw new AppException("GitHub did not return a commit SHA for " + owner + "/" + name + ".");
            }
            return String.valueOf(sha);
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException("Failed to resolve the repository commit: " + ex.getMessage(), ex);
        }
    }

    private byte[] downloadZipball(String owner, String name, String commitSha, String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/" + owner + "/" + name + "/zipball/" + commitSha))
                    .timeout(Duration.ofMinutes(2))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header(HttpHeaders.USER_AGENT, "GitGPT")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400 || response.body() == null || response.body().length == 0) {
                throw new AppException("Failed to download repository snapshot (HTTP " + response.statusCode() + ").");
            }
            return response.body();
        } catch (AppException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AppException("Repository download was interrupted.", ex);
        } catch (Exception ex) {
            throw new AppException("Failed to download the repository snapshot: " + ex.getMessage(), ex);
        }
    }

    private List<SourceFile> readZip(byte[] zip, java.util.UUID repoId) {
        List<SourceFile> files = new ArrayList<>();
        int totalBytes = 0;
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String path = stripRoot(entry.getName());
                byte[] bytes = input.readAllBytes();
                int size = bytes.length;
                size = bytes.length;
                if (!fileFilter.keep(path, size, properties.getMaxFileBytes())) {
                    continue;
                }
                String text = decodeUtf8(bytes);
                if (text == null) {
                    continue;
                }
                totalBytes += size;
                if (files.size() >= properties.getMaxFiles()) {
                    throw new AppException("Repository has more than " + properties.getMaxFiles()
                            + " indexable files. Index a smaller repo for v1.");
                }
                if (totalBytes > properties.getMaxTotalBytes()) {
                    throw new AppException("Repository text exceeds the " + properties.getMaxTotalBytes()
                            + " byte index cap. Index a smaller repo for v1.");
                }
                files.add(new SourceFile(path, fileFilter.language(path), text));
            }
        } catch (AppException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new AppException("Failed to read the repository snapshot zip: " + ex.getMessage(), ex);
        }
        if (files.isEmpty()) {
            log.info("No indexable text files found for repo {}", repoId);
        }
        return files;
    }

    private static String[] splitFullName(String fullName) {
        String[] parts = fullName.split("/", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new AppException("Repository full name is invalid: " + fullName);
        }
        return parts;
    }

    private static String stripRoot(String name) {
        String normalized = name.replace('\\', '/');
        int slash = normalized.indexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private static String decodeUtf8(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            return null;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
