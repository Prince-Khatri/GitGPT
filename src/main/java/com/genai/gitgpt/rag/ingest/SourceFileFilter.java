package com.genai.gitgpt.rag.ingest;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class SourceFileFilter {

    private static final Set<String> SKIP_DIR_PARTS = Set.of(
            ".git",
            "node_modules",
            "target",
            "dist",
            "build",
            "out",
            ".idea",
            ".gradle",
            ".mvn",
            "vendor",
            "__pycache__",
            ".venv",
            "venv",
            "coverage",
            ".next",
            ".turbo"
    );

    private static final Set<String> SKIP_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "ico", "svg", "pdf",
            "zip", "gz", "tgz", "jar", "war", "class", "exe", "dll", "so", "dylib",
            "woff", "woff2", "ttf", "eot", "mp3", "mp4", "mov", "wasm",
            "lock", "map"
    );

    private static final Set<String> SKIP_FILENAMES = Set.of(
            ".env",
            ".env.local",
            ".env.production",
            "id_rsa",
            "id_rsa.pub",
            "credentials.json",
            "package-lock.json",
            "yarn.lock",
            "pnpm-lock.yaml",
            "poetry.lock",
            "gradle-wrapper.jar"
    );

    private static final Set<String> KEEP_EXTENSIONS = Set.of(
            "java", "kt", "kts", "py", "js", "jsx", "ts", "tsx", "go", "rs", "rb", "php",
            "c", "h", "cpp", "cc", "cs", "swift", "sql",
            "md", "txt", "rst",
            "yml", "yaml", "xml", "properties", "gradle", "toml", "json", "html", "css",
            "sh", "bash", "dockerfile"
    );

    public boolean keep(String path, int sizeBytes, int maxFileBytes) {
        if (path == null || path.isBlank() || path.contains("..")) {
            return false;
        }
        String normalized = path.replace('\\', '/');
        String lower = normalized.toLowerCase(Locale.ROOT);
        for (String part : lower.split("/")) {
            if (SKIP_DIR_PARTS.contains(part)) {
                return false;
            }
        }
        String fileName = fileName(lower);
        if (SKIP_FILENAMES.contains(fileName)) {
            return false;
        }
        if (fileName.startsWith(".env") || fileName.endsWith(".pem") || fileName.endsWith(".key")
                || fileName.contains("secret") || fileName.contains("id_rsa")) {
            return false;
        }
        String extension = extension(fileName);
        if (SKIP_EXTENSIONS.contains(extension)) {
            return false;
        }
        if (sizeBytes > maxFileBytes) {
            return false;
        }
        return KEEP_EXTENSIONS.contains(extension) || fileName.equals("dockerfile") || fileName.equals("makefile");
    }

    public String language(String path) {
        String extension = extension(fileName(path.replace('\\', '/').toLowerCase(Locale.ROOT)));
        return extension.isBlank() ? "text" : extension;
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1);
    }
}
