package com.genai.gitgpt.user.gemini;

import com.genai.gitgpt.user.models.Users;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.genai.Client;
import com.google.genai.types.ListModelsConfig;
import com.google.genai.types.Model;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
            log.warn("Could not list Gemini models for this key; using the built-in catalog.");
            return catalog.chatModels();
        }
    }

    List<GeminiModelOption> fetchChatModels(String apiKey) {
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
            log.warn("Gemini model list failed; using the built-in catalog.");
            return catalog.chatModels();
        }
        if (unique.isEmpty()) {
            return catalog.chatModels();
        }
        return sort(List.copyOf(unique.values()));
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
