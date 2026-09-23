package com.genai.gitgpt.user.config;

import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@ConfigurationProperties(prefix = "gitgpt.ui")
public class UiProperties {

    /**
     * Origin of the React app. OAuth success/failure and logout redirect here.
     */
    private String frontendOrigin = "http://localhost:5173";

    public String getFrontendOrigin() {
        String origin = frontendOrigin == null ? "http://localhost:5173" : frontendOrigin.trim();
        while (origin.endsWith("/")) {
            origin = origin.substring(0, origin.length() - 1);
        }
        return origin;
    }

    public String frontendUrl(String path) {
        String origin = getFrontendOrigin();
        if (path == null || path.isBlank()) {
            return origin + "/";
        }
        return origin + (path.startsWith("/") ? path : "/" + path);
    }
}
