package com.genai.gitgpt.user.security;

import com.genai.gitgpt.user.models.Users;
import com.genai.gitgpt.user.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class BearerAuthFilter extends OncePerRequestFilter {

    private final SessionTokenService sessionTokenService;
    private final UserService userService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String githubId = sessionTokenService.parseGithubId(readBearer(request));
            if (githubId != null) {
                userService.findByGithubId(githubId).ifPresent(user -> authenticate(request, user));
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, Users user) {
        OAuth2User principal = toPrincipal(user);
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                principal,
                principal.getAuthorities(),
                "github"
        );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    static OAuth2User toPrincipal(Users user) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("id", user.getGithubId());
        attributes.put("login", user.getGithubUsername() == null ? user.getGithubId() : user.getGithubUsername());
        if (user.getEmail() != null) {
            attributes.put("email", user.getEmail());
        }
        if (user.getUrlAvatar() != null) {
            attributes.put("avatar_url", user.getUrlAvatar());
        }
        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, "id");
    }

    private static String readBearer(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.length() < 8 || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }
}
