package com.genai.gitgpt.user.controller;

import com.genai.gitgpt.exception.ErrorMessages;
import com.genai.gitgpt.user.models.Repo;
import com.genai.gitgpt.user.models.Users;
import com.genai.gitgpt.user.security.OAuthAttributes;
import com.genai.gitgpt.user.service.RepoService;
import com.genai.gitgpt.user.service.UserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class PageController {

    private final UserService userService;
    private final RepoService repoService;

    @GetMapping("/")
    public String index(@AuthenticationPrincipal OAuth2User principal) {
        return principal != null ? "redirect:/home" : "redirect:/login";
    }

    @GetMapping("/login")
    public String login(@AuthenticationPrincipal OAuth2User principal, HttpSession session, Model model) {
        if (principal != null) {
            return "redirect:/home";
        }
        Object errorMessage = session.getAttribute(ErrorMessages.SESSION_KEY);
        if (errorMessage != null) {
            model.addAttribute("errorMessage", errorMessage);
            session.removeAttribute(ErrorMessages.SESSION_KEY);
        }
        return "login";
    }

    @GetMapping("/home")
    public String home(@AuthenticationPrincipal OAuth2User principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        Users user = userService.requireByGithubId(OAuthAttributes.asString(principal, "id"));
        List<Repo> repos = repoService.syncAndList(user);
        model.addAttribute("user", user);
        model.addAttribute("repos", repos);
        return "home";
    }
}
