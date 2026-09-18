package com.genai.gitgpt.user.security;

import com.genai.gitgpt.exception.AppException;
import com.genai.gitgpt.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserService userService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);
        try {
            userService.upsertFromOAuth(
                    oauth2User,
                    userRequest.getAccessToken().getTokenValue(),
                    userRequest.getAccessToken().getScopes()
            );
        } catch (AppException ex) {
            log.error("Failed to persist GitHub user after OAuth callback: {}", ex.getMessage(), ex);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("user_upsert_failed", ex.getMessage(), null),
                    ex
            );
        } catch (OAuth2AuthenticationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Unidentified error while persisting GitHub user after OAuth callback", ex);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(
                            "user_upsert_failed",
                            "Unidentified error (" + ex.getClass().getSimpleName() + "): " + ex.getMessage(),
                            null
                    ),
                    ex
            );
        }
        return oauth2User;
    }
}
