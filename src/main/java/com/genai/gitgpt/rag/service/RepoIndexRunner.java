package com.genai.gitgpt.rag.service;

import com.genai.gitgpt.rag.ingest.Chunker;
import com.genai.gitgpt.rag.ingest.CodeChunk;
import com.genai.gitgpt.rag.ingest.EmbeddingIndexer;
import com.genai.gitgpt.rag.ingest.GitHubSnapshotService;
import com.genai.gitgpt.rag.ingest.SourceFile;
import com.genai.gitgpt.rag.model.IndexJob;
import com.genai.gitgpt.rag.repository.IndexJobRepository;
import com.genai.gitgpt.user.models.IndexStatus;
import com.genai.gitgpt.user.models.Repo;
import com.genai.gitgpt.user.models.Users;
import com.genai.gitgpt.user.repository.RepoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepoIndexRunner {

    private final IndexJobRepository indexJobRepository;
    private final RepoRepository repoRepository;
    private final GitHubSnapshotService snapshotService;
    private final Chunker chunker;
    private final EmbeddingIndexer embeddingIndexer;

    @Async("indexExecutor")
    public void run(UUID jobId) {
        IndexJob job = indexJobRepository.findWithRepoAndUserById(jobId).orElse(null);
        if (job == null) {
            log.error("Index job {} was not found", jobId);
            return;
        }
        Repo repo = job.getRepo();
        Users user = repo.getUser();
        markRunning(job, repo);
        try {
            GitHubSnapshotService.Snapshot snapshot = snapshotService.fetch(user, repo);
            List<CodeChunk> chunks = new ArrayList<>();
            for (SourceFile file : snapshot.files()) {
                chunks.addAll(chunker.chunk(user.getUserID(), repo.getRepoId(), snapshot.commitSha(), file));
            }
            int stored = embeddingIndexer.upsert(user.getUserID(), repo.getRepoId(), snapshot.commitSha(), chunks);
            markReady(job, repo, snapshot.commitSha(), snapshot.files().size(), stored);
            log.info("Indexed repo {} at {} ({} files, {} chunks)", repo.getFullName(), snapshot.commitSha(),
                    snapshot.files().size(), stored);
        } catch (Exception ex) {
            log.error("Index job {} failed for repo {}: {}", jobId, repo.getFullName(), ex.getMessage(), ex);
            markFailed(job, repo, ex.getMessage());
        }
    }

    private void markRunning(IndexJob job, Repo repo) {
        job.setStatus(IndexStatus.RUNNING);
        job.setStartedAt(LocalDateTime.now());
        job.setErrorMessage(null);
        indexJobRepository.save(job);
        repo.setIndexStatus(IndexStatus.RUNNING);
        repo.setIndexError(null);
        repoRepository.save(repo);
    }

    private void markReady(IndexJob job, Repo repo, String commitSha, int fileCount, int chunkCount) {
        LocalDateTime now = LocalDateTime.now();
        job.setStatus(IndexStatus.READY);
        job.setCommitSha(commitSha);
        job.setFileCount(fileCount);
        job.setChunkCount(chunkCount);
        job.setFinishedAt(now);
        job.setErrorMessage(null);
        indexJobRepository.save(job);

        repo.setIndexStatus(IndexStatus.READY);
        repo.setIndexedSha(commitSha);
        repo.setIndexFileCount(fileCount);
        repo.setIndexChunkCount(chunkCount);
        repo.setIndexedAt(now);
        repo.setIndexError(null);
        repoRepository.save(repo);
    }

    private void markFailed(IndexJob job, Repo repo, String message) {
        String error = message == null || message.isBlank() ? "Indexing failed." : message;
        job.setStatus(IndexStatus.FAILED);
        job.setErrorMessage(error);
        job.setFinishedAt(LocalDateTime.now());
        indexJobRepository.save(job);

        repo.setIndexStatus(IndexStatus.FAILED);
        repo.setIndexError(error);
        repoRepository.save(repo);
    }
}
