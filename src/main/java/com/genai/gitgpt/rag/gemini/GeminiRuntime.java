package com.genai.gitgpt.rag.gemini;

import com.genai.gitgpt.exception.AppException;
import com.genai.gitgpt.user.config.GeminiProperties;
import com.genai.gitgpt.user.gemini.GeminiKeyService;
import com.genai.gitgpt.user.gemini.GeminiModelCatalog;
import com.genai.gitgpt.user.models.Users;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.genai.Client;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class GeminiRuntime {

    private final GeminiKeyService geminiKeyService;
    private final GeminiModelCatalog catalog;
    private final GeminiProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final String schema;
    private final Cache<String, CachedModels> models;

    public GeminiRuntime(
            GeminiKeyService geminiKeyService,
            GeminiModelCatalog catalog,
            GeminiProperties properties,
            JdbcTemplate jdbcTemplate,
            @Value("${spring.jpa.properties.hibernate.default_schema}") String schema
    ) {
        this.geminiKeyService = geminiKeyService;
        this.catalog = catalog;
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.schema = schema;
        this.models = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(20))
                .maximumSize(2_000)
                .build();
    }

    public UserAiSession forUser(Users user) {
        geminiKeyService.requireUserKey(user);
        String apiKey = geminiKeyService.requirePlaintext(user);
        String chatModel = catalog.resolveChat(user.getChatModel());
        String embeddingModel = catalog.resolveEmbedding(user.getEmbeddingModel());
        CachedModels cached = models.get(
                cacheKey(user.getUserID(), apiKey, chatModel, embeddingModel),
                ignored -> createModels(apiKey, chatModel, embeddingModel)
        );
        return new UserAiSession(
                cached.chatModel(),
                vectorStore(cached.embeddingModel()),
                cached.embeddingModel(),
                chatModel,
                embeddingModel,
                geminiKeyService.hasUserKey(user)
        );
    }

    public void evict(UUID userId) {
        models.asMap().keySet().removeIf(key -> key.startsWith(String.valueOf(userId) + ':'));
    }

    private CachedModels createModels(String apiKey, String chatModel, String embeddingModel) {
        Client client = Client.builder().apiKey(apiKey).vertexAI(false).build();
        ChatModel chat = GoogleGenAiChatModel.builder()
                .genAiClient(client)
                .options(GoogleGenAiChatOptions.builder().model(chatModel).build())
                .build();
        EmbeddingModel embedding = new GoogleGenAiTextEmbeddingModel(
                GoogleGenAiEmbeddingConnectionDetails.builder()
                        .apiKey(apiKey)
                        .genAiClient(client)
                        .build(),
                GoogleGenAiTextEmbeddingOptions.builder()
                        .model(embeddingModel)
                        .dimensions(properties.getEmbeddingDimensions())
                        .taskType(GoogleGenAiTextEmbeddingOptions.TaskType.RETRIEVAL_DOCUMENT)
                        .build()
        );
        return new CachedModels(chat, embedding);
    }

    private VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(properties.getEmbeddingDimensions())
                .schemaName(schema)
                .vectorTableName("vector_store")
                .initializeSchema(false)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .build();
    }

    private static String cacheKey(UUID userId, String apiKey, String chatModel, String embeddingModel) {
        return userId + ":" + fingerprint(apiKey) + ":" + chatModel + ":" + embeddingModel;
    }

    private static String fingerprint(String apiKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (Exception ex) {
            throw new AppException("Could not prepare the Gemini client.");
        }
    }

    public record UserAiSession(
            ChatModel chatModel,
            VectorStore vectorStore,
            EmbeddingModel embeddingModel,
            String chatModelName,
            String embeddingModelName,
            boolean usingUserKey
    ) {
    }

    private record CachedModels(ChatModel chatModel, EmbeddingModel embeddingModel) {
    }
}
