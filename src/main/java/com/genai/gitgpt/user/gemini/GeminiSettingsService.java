package com.genai.gitgpt.user.gemini;

import com.genai.gitgpt.exception.AppException;
import com.genai.gitgpt.rag.gemini.GeminiRuntime;
import com.genai.gitgpt.user.config.GeminiProperties;
import com.genai.gitgpt.user.dto.UserResponse;
import com.genai.gitgpt.user.models.Users;
import com.genai.gitgpt.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class GeminiSettingsService {

    private static final int MIN_KEY_LENGTH = 20;

    private final UserRepository userRepository;
    private final GeminiKeyService geminiKeyService;
    private final GeminiModelCatalog catalog;
    private final GeminiModelLister modelLister;
    private final GeminiProperties properties;
    private final GeminiKeyVerifier verifier;
    private final GeminiRuntime geminiRuntime;

    public GeminiSettingsService(
            UserRepository userRepository,
            GeminiKeyService geminiKeyService,
            GeminiModelCatalog catalog,
            GeminiModelLister modelLister,
            GeminiProperties properties,
            GeminiKeyVerifier verifier,
            GeminiRuntime geminiRuntime
    ) {
        this.userRepository = userRepository;
        this.geminiKeyService = geminiKeyService;
        this.catalog = catalog;
        this.modelLister = modelLister;
        this.properties = properties;
        this.verifier = verifier;
        this.geminiRuntime = geminiRuntime;
    }

    public GeminiSettingsResponse current(Users user) {
        String chatModel = catalog.resolveChat(user.getChatModel());
        String embeddingModel = catalog.resolveEmbedding(user.getEmbeddingModel());
        if (StringUtils.hasText(user.getGeminiApiKeyHint())) {
            user.setGeminiApiKeyHint(null);
            userRepository.save(user);
        }
        return new GeminiSettingsResponse(
                geminiKeyService.hasUserKey(user),
                geminiKeyService.hasServerKey(),
                null,
                chatModel,
                embeddingModel,
                properties.getEmbeddingDimensions(),
                withSelected(modelLister.chatModelsFor(user), chatModel),
                catalog.embeddingModels()
        );
    }

    public UserResponse toUserResponse(Users user) {
        return new UserResponse(
                user.getUserID(),
                user.getGithubId(),
                user.getEmail(),
                user.getGithubUsername(),
                user.getUrlAvatar(),
                geminiKeyService.hasUserKey(user),
                geminiKeyService.hasServerKey(),
                catalog.resolveChat(user.getChatModel()),
                catalog.resolveEmbedding(user.getEmbeddingModel())
        );
    }

    public UserResponse anonymous(String githubId, String email, String username, String avatar) {
        return new UserResponse(
                null,
                githubId,
                email,
                username,
                avatar,
                false,
                geminiKeyService.hasServerKey(),
                catalog.defaultChatModel(),
                catalog.defaultEmbeddingModel()
        );
    }

    @Transactional
    public GeminiSettingsResponse update(Users user, GeminiSettingsRequest request) {
        if (request == null) {
            throw new AppException("Gemini settings are required.");
        }
        if (StringUtils.hasText(request.apiKey())) {
            String apiKey = normalizeKey(request.apiKey());
            verifier.verify(apiKey);
            geminiKeyService.encryptInto(user, apiKey);
        }
        if (StringUtils.hasText(request.chatModel())) {
            user.setChatModel(catalog.requireChat(request.chatModel()));
        } else if (!StringUtils.hasText(user.getChatModel())) {
            user.setChatModel(catalog.defaultChatModel());
        }
        if (StringUtils.hasText(request.embeddingModel())) {
            user.setEmbeddingModel(catalog.requireEmbedding(request.embeddingModel()));
        } else if (!StringUtils.hasText(user.getEmbeddingModel())) {
            user.setEmbeddingModel(catalog.defaultEmbeddingModel());
        }
        userRepository.save(user);
        geminiRuntime.evict(user.getUserID());
        return current(user);
    }

    @Transactional
    public GeminiSettingsResponse clearKey(Users user) {
        geminiKeyService.clear(user);
        userRepository.save(user);
        geminiRuntime.evict(user.getUserID());
        return current(user);
    }

    private List<GeminiModelOption> withSelected(List<GeminiModelOption> options, String selected) {
        if (!StringUtils.hasText(selected) || options.stream().anyMatch(option -> option.id().equals(selected))) {
            return options;
        }
        List<GeminiModelOption> next = new java.util.ArrayList<>(options);
        next.add(0, catalog.knownChat(selected).orElseGet(() ->
                new GeminiModelOption(selected, GeminiModelCatalog.prettyLabel(selected), "Currently selected.")));
        return List.copyOf(next);
    }

    private static String normalizeKey(String raw) {
        String apiKey = raw == null ? "" : raw.trim();
        if (apiKey.length() < MIN_KEY_LENGTH || apiKey.contains(" ")) {
            throw new AppException("That does not look like a Gemini API key. Paste the key from Google AI Studio.");
        }
        return apiKey;
    }
}
