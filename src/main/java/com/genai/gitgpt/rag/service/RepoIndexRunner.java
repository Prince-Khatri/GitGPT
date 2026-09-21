package com.genai.gitgpt.rag.service;

import com.genai.gitgpt.exception.SecretRedactor;
import com.genai.gitgpt.rag.ingest.Chunker;
import com.genai.gitgpt.rag.ingest.CodeChunk;
import com.genai.gitgpt.rag.ingest.EmbeddingIndexer;
import com.genai.gitgpt.rag.ingest.GitHubSnapshotService;
import com.genai.gitgpt.rag.ingest.SourceFile;
import com.genai.gitgpt.rag.gemini.GeminiRuntime;
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
    private final GeminiRuntime geminiRuntime;

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
            throwIfCancelled(jobId);
            progress(jobId, "CLONE", 12);
            GitHubSnapshotService.Snapshot snapshot = snapshotService.fetch(user, repo);
            throwIfCancelled(jobId);
            progress(jobId, "ANALYZE", 28, snapshot.files().size(), null);
            List<CodeChunk> chunks = new ArrayList<>();
            for (SourceFile file : snapshot.files()) {
                throwIfCancelled(jobId);
                chunks.addAll(chunker.chunk(user.getUserID(), repo.getRepoId(), snapshot.commitSha(), file));
            }
            throwIfCancelled(jobId);
            progress(jobId, "PROCESS", 46, snapshot.files().size(), chunks.size());
            progress(jobId, "EMBED", 67, snapshot.files().size(), chunks.size());
            GeminiRuntime.UserAiSession ai = geminiRuntime.forUser(user);
            int stored = embeddingIndexer.upsert(
                    ai.vectorStore(),
                    user.getUserID(),
                    repo.getRepoId(),
                    snapshot.commitSha(),
                    chunks
            );
            throwIfCancelled(jobId);
            progress(jobId, "STORE", 88, snapshot.files().size(), stored);
            progress(jobId, "FINALIZE", 96, snapshot.files().size(), stored);
            markReady(jobId, repo.getRepoId(), snapshot.commitSha(), snapshot.files().size(), stored, ai.embeddingModelName());
            log.info("Indexed repo {} at {} ({} files, {} chunks)", repo.getFullName(), snapshot.commitSha(),
                    snapshot.files().size(), stored);
        } catch (IndexCancelledException ex) {
            markCancelled(jobId, repo.getRepoId());
        } catch (Exception ex) {
            log.error("Index job {} failed for repo {}: {}", jobId, repo.getFullName(), ex.getMessage(), ex);
            markFailed(jobId, repo.getRepoId(), SecretRedactor.redact(ex.getMessage()));
        }
    }

    private void markRunning(IndexJob job, Repo repo) {
        job.setStatus(IndexStatus.RUNNING);
        job.setStartedAt(LocalDateTime.now());
        job.setErrorMessage(null);
        job.setProgressStep("CLONE");
        job.setProgressPercent(12);
        indexJobRepository.save(job);
        repo.setIndexStatus(IndexStatus.RUNNING);
        repo.setIndexError(null);
        repoRepository.save(repo);
    }

    private void markReady(UUID jobId, UUID repoId, String commitSha, int fileCount, int chunkCount, String embeddingModel) {
        IndexJob job = requireJob(jobId);
        Repo repo = requireRepo(repoId);
        LocalDateTime now = LocalDateTime.now();
        job.setStatus(IndexStatus.READY);
        job.setCommitSha(commitSha);
        job.setFileCount(fileCount);
        job.setChunkCount(chunkCount);
        job.setProgressStep("FINALIZE");
        job.setProgressPercent(100);
        job.setFinishedAt(now);
        job.setErrorMessage(null);
        indexJobRepository.save(job);

        repo.setIndexStatus(IndexStatus.READY);
        repo.setIndexedSha(commitSha);
        repo.setIndexFileCount(fileCount);
        repo.setIndexChunkCount(chunkCount);
        repo.setIndexEmbeddingModel(embeddingModel);
        repo.setIndexedAt(now);
        repo.setIndexError(null);
        repoRepository.save(repo);
    }

    private void markFailed(UUID jobId, UUID repoId, String message) {
        IndexJob job = requireJob(jobId);
        Repo repo = requireRepo(repoId);
        String error = message == null || message.isBlank() ? "Indexing failed." : message;
        job.setStatus(IndexStatus.FAILED);
        job.setErrorMessage(error);
        job.setFinishedAt(LocalDateTime.now());
        indexJobRepository.save(job);

        repo.setIndexStatus(IndexStatus.FAILED);
        repo.setIndexError(error);
        repoRepository.save(repo);
    }

    private void markCancelled(UUID jobId, UUID repoId) {
        IndexJob job = requireJob(jobId);
        Repo repo = requireRepo(repoId);
        job.setStatus(IndexStatus.FAILED);
        job.setErrorMessage("Cancelled.");
        job.setFinishedAt(LocalDateTime.now());
        indexJobRepository.save(job);
        if (repo.getIndexedSha() != null && !repo.getIndexedSha().isBlank()) {
            repo.setIndexStatus(IndexStatus.READY);
            repo.setIndexError(null);
        } else {
            repo.setIndexStatus(IndexStatus.NOT_INDEXED);
            repo.setIndexError(null);
        }
        repoRepository.save(repo);
    }

    private void progress(UUID jobId, String step, int percent) {
        progress(jobId, step, percent, null, null);
    }

    private void progress(UUID jobId, String step, int percent, Integer fileCount, Integer chunkCount) {
        IndexJob job = requireJob(jobId);
        job.setProgressStep(step);
        job.setProgressPercent(percent);
        if (fileCount != null) {
            job.setFileCount(fileCount);
        }
        if (chunkCount != null) {
            job.setChunkCount(chunkCount);
        }
        indexJobRepository.save(job);
    }

    private void throwIfCancelled(UUID jobId) {
        IndexJob job = requireJob(jobId);
        if (job.isCancelRequested()) {
            throw new IndexCancelledException();
        }
    }

    private IndexJob requireJob(UUID jobId) {
        return indexJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Index job " + jobId + " was not found"));
    }

    private Repo requireRepo(UUID repoId) {
        return repoRepository.findById(repoId)
                .orElseThrow(() -> new IllegalStateException("Repo " + repoId + " was not found"));
    }

    private static final class IndexCancelledException extends RuntimeException {
    }
}
