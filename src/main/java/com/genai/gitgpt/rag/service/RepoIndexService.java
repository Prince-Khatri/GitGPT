package com.genai.gitgpt.rag.service;

import com.genai.gitgpt.exception.AppException;
import com.genai.gitgpt.rag.dto.IndexJobResponse;
import com.genai.gitgpt.rag.model.IndexJob;
import com.genai.gitgpt.rag.repository.IndexJobRepository;
import com.genai.gitgpt.user.models.IndexStatus;
import com.genai.gitgpt.user.models.Repo;
import com.genai.gitgpt.user.models.Users;
import com.genai.gitgpt.user.repository.RepoRepository;
import com.genai.gitgpt.user.service.RepoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.EnumSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RepoIndexService {

    private static final EnumSet<IndexStatus> ACTIVE = EnumSet.of(IndexStatus.QUEUED, IndexStatus.RUNNING);

    private final RepoService repoService;
    private final RepoRepository repoRepository;
    private final IndexJobRepository indexJobRepository;
    private final RepoIndexRunner repoIndexRunner;

    @Transactional
    public IndexJobResponse enqueue(Users user, UUID repoId) {
        Repo repo = repoService.requireOwned(user, repoId);
        IndexJob existing = indexJobRepository.findTopByRepoOrderByCreatedAtDesc(repo).orElse(null);
        if (existing != null && ACTIVE.contains(existing.getStatus())) {
            return toResponse(existing);
        }
        if (indexJobRepository.existsByUserAndStatusIn(user, ACTIVE)) {
            throw new AppException("An index job is already running. Wait for it to finish before starting another.");
        }

        IndexJob job = indexJobRepository.saveAndFlush(IndexJob.builder()
                .user(user)
                .repo(repo)
                .status(IndexStatus.QUEUED)
                .build());
        repo.setIndexStatus(IndexStatus.QUEUED);
        repo.setIndexError(null);
        repoRepository.saveAndFlush(repo);

        UUID jobId = job.getJobId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    repoIndexRunner.run(jobId);
                }
            });
        } else {
            repoIndexRunner.run(jobId);
        }
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public IndexJobResponse status(Users user, UUID repoId) {
        Repo repo = repoService.requireOwned(user, repoId);
        IndexJob job = indexJobRepository.findTopByRepoOrderByCreatedAtDesc(repo)
                .orElseThrow(() -> new AppException("This repository has not been indexed yet."));
        return toResponse(job);
    }

    public static IndexJobResponse toResponse(IndexJob job) {
        return new IndexJobResponse(
                job.getJobId(),
                job.getRepo().getRepoId(),
                job.getStatus(),
                job.getCommitSha(),
                job.getFileCount(),
                job.getChunkCount(),
                job.getErrorMessage(),
                job.getStartedAt(),
                job.getFinishedAt()
        );
    }
}
