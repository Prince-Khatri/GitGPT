package com.genai.gitgpt.user.security;

import com.genai.gitgpt.user.config.SecurityProperties;
import com.genai.gitgpt.user.config.UiProperties;
import org.springframework.boot.web.server.servlet.CookieSameSiteSupplier;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SessionCookieConfig {

    @Bean
    CookieSameSiteSupplier sessionCookieSameSite(UiProperties uiProperties, SecurityProperties securityProperties) {
        String sameSite = securityProperties.effectiveCookieSameSite(uiProperties.getFrontendOrigin());
        return switch (sameSite.toLowerCase()) {
            case "none" -> CookieSameSiteSupplier.ofNone();
            case "strict" -> CookieSameSiteSupplier.ofStrict();
            default -> CookieSameSiteSupplier.ofLax();
        };
    }

    @Bean
    ServletContextInitializer sessionCookieSecure(UiProperties uiProperties, SecurityProperties securityProperties) {
        boolean secure = securityProperties.effectiveCookieSecure(uiProperties.getFrontendOrigin());
        return servletContext -> servletContext.getSessionCookieConfig().setSecure(secure);
    }
}
