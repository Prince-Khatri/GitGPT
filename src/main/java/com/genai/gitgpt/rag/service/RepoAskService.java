package com.genai.gitgpt.rag.service;

import com.genai.gitgpt.exception.AppException;
import com.genai.gitgpt.rag.config.AskProperties;
import com.genai.gitgpt.rag.dto.AskResponse;
import com.genai.gitgpt.rag.dto.CitationResponse;
import com.genai.gitgpt.rag.retrieve.AnswerGenerator;
import com.genai.gitgpt.rag.retrieve.CandidateFinder;
import com.genai.gitgpt.rag.retrieve.ContextPacker;
import com.genai.gitgpt.rag.retrieve.QueryPlan;
import com.genai.gitgpt.rag.retrieve.QueryPlanner;
import com.genai.gitgpt.rag.retrieve.RetrievedChunk;
import com.genai.gitgpt.rag.retrieve.VectorRetriever;
import com.genai.gitgpt.user.models.IndexStatus;
import com.genai.gitgpt.user.models.Repo;
import com.genai.gitgpt.user.models.Users;
import com.genai.gitgpt.user.service.RepoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepoAskService {

    private final RepoService repoService;
    private final AskProperties askProperties;
    private final QueryPlanner queryPlanner;
    private final CandidateFinder candidateFinder;
    private final VectorRetriever vectorRetriever;
    private final ContextPacker contextPacker;
    private final AnswerGenerator answerGenerator;

    public AskResponse ask(Users user, UUID repoId, String question) {
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

        QueryPlan plan = queryPlanner.plan(trimmed);
        List<RetrievedChunk> keywords = candidateFinder.find(
                user.getUserID(), repo.getRepoId(), repo.getIndexedSha(), plan);
        List<RetrievedChunk> vectors = vectorRetriever.search(
                user.getUserID(),
                repo.getRepoId(),
                repo.getIndexedSha(),
                plan.rewrittenQuery(),
                plan.pathHints()
        );
        List<RetrievedChunk> packed = contextPacker.pack(keywords, vectors);
        boolean grounded = !packed.isEmpty();
        String context = contextPacker.format(packed);
        String answer = answerGenerator.generate(trimmed, plan.intent(), context);
        log.info("Ask repo {} intent={} keywords={} vectors={} packed={}",
                repo.getFullName(), plan.intent(), keywords.size(), vectors.size(), packed.size());
        return new AskResponse(
                repo.getRepoId(),
                repo.getFullName(),
                trimmed,
                plan.intent(),
                plan.rewrittenQuery(),
                answer,
                grounded,
                packed.stream()
                        .map(chunk -> new CitationResponse(
                                chunk.path(),
                                chunk.startLine(),
                                chunk.endLine(),
                                chunk.commitSha(),
                                chunk.source()
                        ))
                        .toList()
        );
    }
}
