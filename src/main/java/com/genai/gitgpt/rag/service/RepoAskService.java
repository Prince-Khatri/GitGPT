package com.genai.gitgpt.rag.service;

import com.genai.gitgpt.exception.AppException;
import com.genai.gitgpt.exception.GeminiErrors;
import com.genai.gitgpt.rag.config.AskProperties;
import com.genai.gitgpt.rag.dto.AskResponse;
import com.genai.gitgpt.rag.dto.CitationResponse;
import com.genai.gitgpt.rag.model.ChatSession;
import com.genai.gitgpt.rag.retrieve.AnswerGenerator;
import com.genai.gitgpt.rag.retrieve.CandidateFinder;
import com.genai.gitgpt.rag.retrieve.ContextPacker;
import com.genai.gitgpt.rag.retrieve.FollowUpQuery;
import com.genai.gitgpt.rag.retrieve.GitHubLinks;
import com.genai.gitgpt.rag.retrieve.QueryPlan;
import com.genai.gitgpt.rag.retrieve.QueryPlanner;
import com.genai.gitgpt.rag.retrieve.RetrievedChunk;
import com.genai.gitgpt.rag.retrieve.VectorRetriever;
import com.genai.gitgpt.rag.gemini.GeminiRuntime;
import com.genai.gitgpt.user.models.IndexStatus;
import com.genai.gitgpt.user.models.Repo;
import com.genai.gitgpt.user.models.Users;
import com.genai.gitgpt.user.security.RateLimitService;
import com.genai.gitgpt.user.service.RepoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

@Service
@Slf4j
public class RepoAskService {

    private final RepoService repoService;
    private final ChatService chatService;
    private final AskProperties askProperties;
    private final QueryPlanner queryPlanner;
    private final CandidateFinder candidateFinder;
    private final VectorRetriever vectorRetriever;
    private final ContextPacker contextPacker;
    private final AnswerGenerator answerGenerator;
    private final GeminiRuntime geminiRuntime;
    private final RateLimitService rateLimitService;
    private final Executor retrieveExecutor;

    public RepoAskService(
            RepoService repoService,
            ChatService chatService,
            AskProperties askProperties,
            QueryPlanner queryPlanner,
            CandidateFinder candidateFinder,
            VectorRetriever vectorRetriever,
            ContextPacker contextPacker,
            AnswerGenerator answerGenerator,
            GeminiRuntime geminiRuntime,
            RateLimitService rateLimitService,
            @Qualifier("retrieveExecutor") Executor retrieveExecutor
    ) {
        this.repoService = repoService;
        this.chatService = chatService;
        this.askProperties = askProperties;
        this.queryPlanner = queryPlanner;
        this.candidateFinder = candidateFinder;
        this.vectorRetriever = vectorRetriever;
        this.contextPacker = contextPacker;
        this.answerGenerator = answerGenerator;
        this.geminiRuntime = geminiRuntime;
        this.rateLimitService = rateLimitService;
        this.retrieveExecutor = retrieveExecutor;
    }

    public AskResponse ask(Users user, UUID repoId, String question, UUID sessionId) {
        PreparedAsk prepared = prepare(user, repoId, question, sessionId);
        String answer;
        try {
            answer = answerGenerator.generate(
                    prepared.ai().chatModel(),
                    prepared.question(),
                    prepared.plan().intent(),
                    prepared.context(),
                    prepared.history()
            );
        } catch (Exception ex) {
            throw GeminiErrors.wrap(ex);
        }
        persistAssistant(prepared, answer);
        return toResponse(prepared, answer);
    }

    public void checkAsk(Users user) {
        rateLimitService.checkAsk(user.getUserID());
    }

    public PreparedAsk prepare(Users user, UUID repoId, String question, UUID sessionId) {
        return prepare(user, repoId, question, sessionId, true);
    }

    public PreparedAsk prepare(Users user, UUID repoId, String question, UUID sessionId, boolean enforceLimit) {
        if (enforceLimit) {
            rateLimitService.checkAsk(user.getUserID());
        }
        String trimmed = question == null ? "" : question.trim();
        if (trimmed.isBlank()) {
            throw new AppException("Ask a question about the indexed repository.");
        }
        if (trimmed.length() > askProperties.getMaxQuestionChars()) {
            throw new AppException("Question is too long. Keep it under "
                    + askProperties.getMaxQuestionChars() + " characters.");
        }
        Repo repo = repoService.requireOwned(user, repoId);
        if (repo.getIndexStatus() != IndexStatus.READY || repo.getIndexedSha() == null || repo.getIndexedSha().isBlank()) {
            throw new AppException("Index this repository first. Questions run only against a READY snapshot.");
        }

        GeminiRuntime.UserAiSession ai = geminiRuntime.forUser(user);
        ChatSession session = chatService.open(user, repo, sessionId);
        List<AnswerGenerator.ChatTurn> history = chatService.recentTurns(session);
        String retrievalQuestion = FollowUpQuery.forRetrieval(chatService.priorUserQuestions(session), trimmed);
        QueryPlan plan;
        try {
            plan = queryPlanner.plan(retrievalQuestion, ai.chatModel());
        } catch (Exception ex) {
            throw GeminiErrors.wrap(ex);
        }
        CompletableFuture<List<RetrievedChunk>> keywordFuture = CompletableFuture.supplyAsync(
                () -> candidateFinder.find(user.getUserID(), repo.getRepoId(), repo.getIndexedSha(), plan),
                retrieveExecutor
        );
        CompletableFuture<List<RetrievedChunk>> vectorFuture = CompletableFuture.supplyAsync(
                () -> vectorRetriever.search(
                        ai.vectorStore(),
                        user.getUserID(),
                        repo.getRepoId(),
                        repo.getIndexedSha(),
                        plan.rewrittenQuery(),
                        plan.pathHints()
                ),
                retrieveExecutor
        );
        List<RetrievedChunk> keywords = keywordFuture.join();
        List<RetrievedChunk> vectors = vectorFuture.join();
        List<RetrievedChunk> packed = contextPacker.pack(keywords, vectors);
        chatService.appendUser(session, trimmed);
        log.info("Ask repo {} session={} intent={} keywords={} vectors={} packed={} skipPlanner={}",
                repo.getFullName(), session.getSessionId(), plan.intent(), keywords.size(), vectors.size(),
                packed.size(), QueryPlanner.hasStrongIdentifiers(retrievalQuestion));
        return new PreparedAsk(
                repo,
                session,
                trimmed,
                plan,
                packed,
                !packed.isEmpty(),
                contextPacker.format(packed),
                history,
                ai
        );
    }

    public void streamAnswer(PreparedAsk prepared, Consumer<String> onDelta) {
        StringBuilder full = new StringBuilder();
        try {
            answerGenerator.stream(
                    prepared.ai().chatModel(),
                    prepared.question(),
                    prepared.plan().intent(),
                    prepared.context(),
                    prepared.history(),
                    delta -> {
                        full.append(delta);
                        onDelta.accept(delta);
                    }
            );
        } catch (Exception ex) {
            throw GeminiErrors.wrap(ex);
        }
        persistAssistant(prepared, full.toString());
    }

    public AskResponse toResponse(PreparedAsk prepared, String answer) {
        return new AskResponse(
                prepared.repo().getRepoId(),
                prepared.session().getSessionId(),
                prepared.repo().getFullName(),
                prepared.question(),
                prepared.plan().intent(),
                prepared.plan().rewrittenQuery(),
                answer,
                prepared.grounded(),
                citationsOf(prepared)
        );
    }

    private void persistAssistant(PreparedAsk prepared, String answer) {
        chatService.appendAssistant(
                prepared.session(),
                answer,
                prepared.plan().intent(),
                prepared.grounded(),
                citationsOf(prepared)
        );
    }

    private static List<CitationResponse> citationsOf(PreparedAsk prepared) {
        return prepared.packed().stream()
                .map(chunk -> new CitationResponse(
                        chunk.path(),
                        chunk.startLine(),
                        chunk.endLine(),
                        chunk.commitSha(),
                        chunk.source(),
                        GitHubLinks.blob(
                                prepared.repo().getFullName(),
                                chunk.commitSha(),
                                chunk.path(),
                                chunk.startLine(),
                                chunk.endLine()
                        )
                ))
                .toList();
    }

    public record PreparedAsk(
            Repo repo,
            ChatSession session,
            String question,
            QueryPlan plan,
            List<RetrievedChunk> packed,
            boolean grounded,
            String context,
            List<AnswerGenerator.ChatTurn> history,
            GeminiRuntime.UserAiSession ai
    ) {
    }
}
