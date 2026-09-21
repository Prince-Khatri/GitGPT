package com.genai.gitgpt.user.controller;

import com.genai.gitgpt.user.config.UiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ApiRootController {

    private final UiProperties uiProperties;

    @GetMapping("/")
    public Map<String, String> root() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("service", "GitGPT API");
        payload.put("ui", uiProperties.getFrontendOrigin());
        return payload;
    }
}
