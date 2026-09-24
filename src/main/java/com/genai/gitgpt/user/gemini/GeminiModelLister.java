package com.genai.gitgpt.user.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genai.gitgpt.exception.SecretRedactor;
import com.genai.gitgpt.user.models.Users;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.genai.Client;
import com.google.genai.types.ListModelsConfig;
import com.google.genai.types.Model;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Chat models the signed-in user's Gemini key can actually call.
 * Falls back to {@link GeminiModelCatalog} if the key is missing or list fails.
 */
@Slf4j
@Component
public class GeminiModelLister {

    private static final int MAX_CHAT_OPTIONS = 60;

    private final GeminiKeyService geminiKeyService;
    private final GeminiModelCatalog catalog;
    private final Cache<String, List<GeminiModelOption>> byKey;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    public GeminiModelLister(GeminiKeyService geminiKeyService, GeminiModelCatalog catalog) {
        this.geminiKeyService = geminiKeyService;
        this.catalog = catalog;
        this.byKey = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(15))
                .maximumSize(2_000)
                .build();
    }

    public List<GeminiModelOption> chatModelsFor(Users user) {
        if (!geminiKeyService.hasUserKey(user)) {
            return catalog.chatModels();
        }
        try {
            String apiKey = geminiKeyService.requirePlaintext(user);
            return byKey.get(fingerprint(apiKey), ignored -> fetchChatModels(apiKey));
        } catch (Exception ex) {
            log.warn("Could not list Gemini models for this key; using the built-in catalog. {}",
                    SecretRedactor.redact(ex.getMessage()));
            return catalog.chatModels();
        }
    }

    List<GeminiModelOption> fetchChatModels(String apiKey) {
        try {
            List<GeminiModelOption> fromApi = fetchViaRest(apiKey);
            if (!fromApi.isEmpty()) {
                return fromApi;
            }
        } catch (Exception ex) {
            log.warn("Gemini REST model list failed: {}", SecretRedactor.redact(ex.getMessage()));
        }
        return fetchViaSdk(apiKey);
    }

    private List<GeminiModelOption> fetchViaSdk(String apiKey) {
        Client client = Client.builder().apiKey(apiKey).vertexAI(false).build();
        Map<String, GeminiModelOption> unique = new LinkedHashMap<>();
        try {
            for (Model model : client.models.list(ListModelsConfig.builder().queryBase(true).pageSize(100).build())) {
                toChatOption(model).ifPresent(option -> unique.putIfAbsent(option.id(), option));
                if (unique.size() >= MAX_CHAT_OPTIONS) {
                    break;
                }
            }
        } catch (Exception ex) {
            log.warn("Gemini SDK model list failed: {}", SecretRedactor.redact(ex.getMessage()));
            return catalog.chatModels();
        }
        if (unique.isEmpty()) {
            return catalog.chatModels();
        }
        return sort(List.copyOf(unique.values()));
    }

    private List<GeminiModelOption> fetchViaRest(String apiKey) {
        String body = restClient.get()
                .uri("https://generativelanguage.googleapis.com/v1beta/models?pageSize=100")
                .header(HttpHeaders.ACCEPT, "application/json")
                .header("x-goog-api-key", apiKey)
                .retrieve()
                .body(String.class);
        return parseRestModels(body);
    }

    List<GeminiModelOption> parseRestModels(String json) {
        try {
            JsonNode models = objectMapper.readTree(json == null ? "{}" : json).path("models");
            Map<String, GeminiModelOption> unique = new LinkedHashMap<>();
            if (models.isArray()) {
                for (JsonNode model : models) {
                    if (!supportsGenerate(model)) {
                        continue;
                    }
                    String id = GeminiModelCatalog.normalizeId(model.path("name").asText(""));
                    if (!GeminiModelCatalog.isChatModelId(id)) {
                        continue;
                    }
                    unique.putIfAbsent(id, catalog.knownChat(id).orElseGet(() -> new GeminiModelOption(
                            id,
                            textOr(model, "displayName", GeminiModelCatalog.prettyLabel(id)),
                            textOr(model, "description", "Available on your Gemini API key.")
                    )));
                    if (unique.size() >= MAX_CHAT_OPTIONS) {
                        break;
                    }
                }
            }
            if (unique.isEmpty()) {
                return List.of();
            }
            return sort(List.copyOf(unique.values()));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not parse Gemini model list.");
        }
    }

    private static boolean supportsGenerate(JsonNode model) {
        JsonNode methods = model.path("supportedGenerationMethods");
        if (!methods.isArray() || methods.isEmpty()) {
            return true;
        }
        for (JsonNode method : methods) {
            if (canGenerate(method.asText(""))) {
                return true;
            }
        }
        return false;
    }

    private static String textOr(JsonNode model, String field, String fallback) {
        String value = model.path(field).asText("");
        return StringUtils.hasText(value) ? value : fallback;
    }

    Optional<GeminiModelOption> toChatOption(Model model) {
        if (model == null) {
            return Optional.empty();
        }
        String id = GeminiModelCatalog.normalizeId(model.name().orElse(""));
        if (!GeminiModelCatalog.isChatModelId(id)) {
            return Optional.empty();
        }
        if (model.supportedActions().isPresent()
                && !model.supportedActions().get().isEmpty()
                && model.supportedActions().get().stream().noneMatch(GeminiModelLister::canGenerate)) {
            return Optional.empty();
        }
        return Optional.of(catalog.knownChat(id).orElseGet(() -> new GeminiModelOption(
                id,
                model.displayName().filter(StringUtils::hasText).orElseGet(() -> GeminiModelCatalog.prettyLabel(id)),
                model.description().filter(StringUtils::hasText).orElse("Available on your Gemini API key.")
        )));
    }

    private List<GeminiModelOption> sort(List<GeminiModelOption> listed) {
        Map<String, Integer> preferred = new LinkedHashMap<>();
        List<GeminiModelOption> catalogModels = catalog.chatModels();
        for (int i = 0; i < catalogModels.size(); i++) {
            preferred.put(catalogModels.get(i).id(), i);
        }
        List<GeminiModelOption> sorted = new ArrayList<>(listed);
        sorted.sort(Comparator
                .comparingInt((GeminiModelOption option) -> preferred.getOrDefault(option.id(), 1_000))
                .thenComparing(GeminiModelOption::id, Comparator.reverseOrder()));
        return List.copyOf(sorted);
    }

    private static boolean canGenerate(String action) {
        if (!StringUtils.hasText(action)) {
            return false;
        }
        String value = action.toLowerCase();
        return value.contains("generatecontent") || value.equals("generate") || value.contains("predict");
    }

    private static String fingerprint(String apiKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (Exception ex) {
            return "key";
        }
    }
}
