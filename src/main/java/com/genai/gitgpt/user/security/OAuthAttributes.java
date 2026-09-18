package com.genai.gitgpt.user.security;

import org.springframework.security.oauth2.core.user.OAuth2User;

public final class OAuthAttributes {

    private OAuthAttributes() {
    }

    public static String asString(OAuth2User user, String key) {
        if (user == null) {
            return null;
        }
        Object value = user.getAttributes().get(key);
        return value == null ? null : String.valueOf(value);
    }
}
