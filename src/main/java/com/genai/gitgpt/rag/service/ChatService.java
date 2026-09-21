package com.genai.gitgpt.rag.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genai.gitgpt.exception.AppException;
import com.genai.gitgpt.rag.config.AskProperties;
import com.genai.gitgpt.rag.dto.ChatHistoryItem;
import com.genai.gitgpt.rag.dto.ChatMessageResponse;
import com.genai.gitgpt.rag.dto.CitationResponse;
import com.genai.gitgpt.rag.retrieve.GitHubLinks;
import com.genai.gitgpt.rag.model.ChatMessage;
import com.genai.gitgpt.rag.model.ChatSession;
import com.genai.gitgpt.rag.model.MessageRole;
import com.genai.gitgpt.rag.repository.ChatMessageRepository;
import com.genai.gitgpt.rag.repository.ChatSessionRepository;
import com.genai.gitgpt.rag.retrieve.AnswerGenerator;
import com.genai.gitgpt.user.models.Repo;
import com.genai.gitgpt.user.models.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final TypeReference<List<CitationResponse>> CITATIONS_TYPE = new TypeReference<>() {};

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final AskProperties askProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public ChatSession open(Users user, Repo repo, UUID sessionId) {
        String sha = requireSha(repo);
        if (sessionId != null) {
            ChatSession existing = sessionRepository.findBySessionIdAndUser(sessionId, user).orElse(null);
            if (existing != null && repo.getRepoId().equals(existing.getRepo().getRepoId())
                    && sha.equals(existing.getCommitSha())) {
                return existing;
            }
        }
        return sessionRepository.findFirstByUserAndRepoAndCommitShaOrderByUpdatedAtDesc(user, repo, sha)
                .orElseGet(() -> create(user, repo, sha));
    }

    @Transactional
    public ChatSession startNew(Users user, Repo repo) {
        return create(user, repo, requireSha(repo));
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> messages(ChatSession session) {
        return messageRepository.findBySessionOrderByCreatedAtAsc(session);
    }

    @Transactional(readOnly = true)
    public List<ChatHistoryItem> history(Users user) {
        return sessionRepository.findHistoryByUser(user).stream().map(this::toHistory).toList();
    }

    @Transactional(readOnly = true)
    public List<ChatHistoryItem> history(Users user, Repo repo) {
        return sessionRepository.findHistoryByUserAndRepo(user, repo).stream().map(this::toHistory).toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> toResponses(ChatSession session) {
        String fullName = session.getRepo() == null ? null : session.getRepo().getFullName();
        return messages(session).stream().map(message -> toResponse(message, fullName)).toList();
    }

    @Transactional(readOnly = true)
    public List<String> priorUserQuestions(ChatSession session) {
        return messages(session).stream()
                .filter(message -> message.getRole() == MessageRole.USER)
                .map(ChatMessage::getContent)
                .filter(content -> content != null && !content.isBlank())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AnswerGenerator.ChatTurn> recentTurns(ChatSession session) {
        List<ChatMessage> all = messages(session);
        int max = Math.max(0, askProperties.getMaxHistoryMessages());
        int from = Math.max(0, all.size() - max);
        List<AnswerGenerator.ChatTurn> turns = new ArrayList<>();
        for (ChatMessage message : all.subList(from, all.size())) {
            String content = clip(message.getContent());
            if (content.isBlank()) {
                continue;
            }
            String role = message.getRole() == MessageRole.USER ? "User" : "Assistant";
            turns.add(new AnswerGenerator.ChatTurn(role, content));
        }
        return turns;
    }

    @Transactional
    public ChatMessage appendUser(ChatSession session, String content) {
        if (session.getTitle() == null || session.getTitle().isBlank()) {
            session.setTitle(clipTitle(content));
        }
        ChatMessage saved = messageRepository.save(ChatMessage.builder()
                .session(session)
                .role(MessageRole.USER)
                .content(content)
                .build());
        touch(session);
        return saved;
    }

    @Transactional
    public ChatMessage appendAssistant(
            ChatSession session,
            String content,
            String intent,
            boolean grounded,
            List<CitationResponse> citations
    ) {
        ChatMessage saved = messageRepository.save(ChatMessage.builder()
                .session(session)
                .role(MessageRole.ASSISTANT)
                .content(content == null ? "" : content)
                .intent(intent)
                .grounded(grounded)
                .citationsJson(writeCitations(citations))
                .build());
        touch(session);
        return saved;
    }

    public ChatMessageResponse toResponse(ChatMessage message) {
        return toResponse(message, null);
    }

    public ChatMessageResponse toResponse(ChatMessage message, String repoFullName) {
        return new ChatMessageResponse(
                message.getMessageId(),
                message.getRole().name(),
                message.getContent(),
                message.getIntent(),
                message.getGrounded(),
                withGithubUrls(readCitations(message.getCitationsJson()), repoFullName),
                message.getCreatedAt()
        );
    }

    public ChatHistoryItem toHistory(ChatSession session) {
        String title = session.getTitle();
        if (title == null || title.isBlank()) {
            title = "Conversation";
        }
        return new ChatHistoryItem(
                session.getSessionId(),
                session.getRepo().getRepoId(),
                session.getRepo().getFullName(),
                title,
                session.getCommitSha(),
                session.getUpdatedAt()
        );
    }

    private ChatSession create(Users user, Repo repo, String sha) {
        return sessionRepository.save(ChatSession.builder()
                .user(user)
                .repo(repo)
                .commitSha(sha)
                .build());
    }

    private void touch(ChatSession session) {
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
    }

    private List<CitationResponse> withGithubUrls(List<CitationResponse> citations, String fullName) {
        return citations.stream().map(citation -> {
            if (citation.githubUrl() != null && !citation.githubUrl().isBlank()) {
                return citation;
            }
            return new CitationResponse(
                    citation.path(),
                    citation.startLine(),
                    citation.endLine(),
                    citation.commitSha(),
                    citation.source(),
                    GitHubLinks.blob(fullName, citation.commitSha(), citation.path(), citation.startLine(), citation.endLine())
            );
        }).toList();
    }

    private String clipTitle(String content) {
        if (content == null) {
            return "Conversation";
        }
        String trimmed = content.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80);
    }

    private String clip(String content) {
        if (content == null) {
            return "";
        }
        int max = Math.max(80, askProperties.getMaxHistoryChars());
        String trimmed = content.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private String writeCitations(List<CitationResponse> citations) {
        try {
            return objectMapper.writeValueAsString(citations == null ? List.of() : citations);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private List<CitationResponse> readCitations(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<CitationResponse> parsed = objectMapper.readValue(json, CITATIONS_TYPE);
            return parsed == null ? List.of() : parsed;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static String requireSha(Repo repo) {
        if (repo.getIndexedSha() == null || repo.getIndexedSha().isBlank()) {
            throw new AppException("Index this repository first. Questions run only against a READY snapshot.");
        }
        return repo.getIndexedSha();
    }
}
