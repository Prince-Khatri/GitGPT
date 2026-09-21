package com.genai.gitgpt.rag.retrieve;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class GitHubLinks {

    private GitHubLinks() {
    }

    public static String blob(String fullName, String commitSha, String path, int startLine, int endLine) {
        if (fullName == null || fullName.isBlank() || commitSha == null || commitSha.isBlank()
                || path == null || path.isBlank()) {
            return null;
        }
        String encodedPath = encodePath(path);
        String url = "https://github.com/" + fullName + "/blob/" + commitSha + "/" + encodedPath;
        if (startLine > 0) {
            url += "#L" + startLine;
            if (endLine > startLine) {
                url += "-L" + endLine;
            }
        }
        return url;
    }

    private static String encodePath(String path) {
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        StringBuilder encoded = new StringBuilder();
        for (String part : normalized.split("/")) {
            if (encoded.length() > 0) {
                encoded.append('/');
            }
            encoded.append(URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return encoded.toString();
    }
}
