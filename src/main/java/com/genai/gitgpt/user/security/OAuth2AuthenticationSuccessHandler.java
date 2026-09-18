package com.genai.gitgpt.user.security;

import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    public OAuth2AuthenticationSuccessHandler() {
        setDefaultTargetUrl("/home");
        setAlwaysUseDefaultTargetUrl(true);
    }
}
