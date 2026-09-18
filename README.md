# GitGPT

GitGPT lets you sign in with GitHub, pick **one repository**, index it, and ask questions about that snapshot. Answers are grounded in retrieved code chunks, not a live crawl of GitHub.

## What it does

1. **GitHub login** — OAuth with `read:user`, `user:email`, and `repo`.
2. **Repo list** — `/home` syncs repositories you can access. Listing does **not** embed anything.
3. **Index** — you choose a repo. An async job snapshots the default branch (commit SHA), filters files, chunks them, and stores embeddings in Postgres/pgvector.
4. **Ask** — only when status is `READY`. Retrieval is: optional query plan → keyword/path candidates ∥ vector search (scoped to you + that repo + SHA) → pack a few chunks → Gemini. The page is a persisted thread: each turn retrieves again; history is only for follow-ups. The answer streams over SSE.

Index status: `NOT_INDEXED` → `QUEUED` → `RUNNING` → `READY` | `FAILED`.

## Stack

- Java 17, Spring Boot 4.1, Thymeleaf, Spring Security OAuth2
- PostgreSQL + [pgvector](https://github.com/pgvector/pgvector)
- Spring AI 2 with Google Gemini (`gemini-3.5-flash-lite` chat, `gemini-embedding-001` at 1536 dimensions)

Packages:

- `com.genai.gitgpt.user` — OAuth, users, repo catalog
- `com.genai.gitgpt.rag` — index jobs, ingest, retrieve, ask

## Prerequisites

- JDK 17+
- PostgreSQL with the `vector` extension enabled in your schema
- A [GitHub OAuth App](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/creating-an-oauth-app)
  - Homepage URL: `http://localhost:8080`
  - Authorization callback URL: `http://localhost:8080/login/oauth2/code/github`
- A [Google AI Studio](https://aistudio.google.com/apikey) API key

Create the schema (example) and extension:

```sql
CREATE SCHEMA IF NOT EXISTS gitgpt_schema_t1;
CREATE EXTENSION IF NOT EXISTS vector;
```

`initialize-schema=true` will create the `vector_store` table on startup. Hibernate `ddl-auto=update` creates app tables.

## Configuration

Set these environment variables (no secrets in the repo):

| Variable | Purpose |
|---|---|
| `DB_URL` | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/postgres` |
| `DB_SCHEMA` | Schema for JPA and pgvector |
| `DB_USERNAME` | Database user |
| `DB_PASSWORD` | Database password |
| `GOOGLE_API_KEY` | Gemini API key |
| `GITHUB_CLIENT_ID` | GitHub OAuth client id |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth client secret |
| `GITGPT_TOKEN_ENCRYPTION_KEY` | Token encryption key. Local fallback is `gitgpt-local-dev-only-not-for-prod`. Best for real use: `openssl rand -base64 32`. |

Index caps (see `application.properties`): at most 400 files, 256 KB per file, 8 MB total text. Embeddings use 1536 dimensions because pgvector HNSW tops out at 2000 (Gemini’s default is 3072).

## Run

```bash
./mvnw spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080), sign in with GitHub, index a **small** repo first, wait until `READY`, then **Ask**.

```bash
./mvnw test
```

## Ask pipeline

1. If the question already contains identifiers (`UserService`, `Foo.java`), skip the LLM planner.
2. Keyword/path search and vector search run **in parallel**, filtered by `userId`, `repoId`, and `commitSha`.
3. Overlapping windows are dropped; about 5 chunks are sent to the model.
4. The UI streams tokens (`POST /repos/{repoId}/ask/stream`). Citations come from retrieval, not from guessing.
5. Follow-ups stay in the same `(user, repo, snapshot)` session. Retrieval still runs every turn.

The model is instructed to answer only from those chunks.

## HTTP

| Method | Path | Notes |
|---|---|---|
| GET | `/` `/login` `/home` | Pages |
| POST | `/repos/{repoId}/index` | Queue an index job |
| GET | `/repos/{repoId}` | Chat page (READY only) |
| POST | `/repos/{repoId}/ask/stream` | SSE answer (`sessionId` optional) |
| POST | `/repos/{repoId}/chat/new` | Start a new thread |
| GET | `/api/me` | Current user |
| GET | `/api/repos` | Sync + list repos |
| GET/POST | `/api/repos/{repoId}/index` | Index status / start |
| POST | `/api/repos/{repoId}/ask` | JSON `{ "question": "...", "sessionId": "..." }` |
| GET | `/api/repos/{repoId}/chat` | Thread messages |

## Limits (current)

- GitHub access tokens are AES-GCM encrypted at rest. Decrypt only in memory for GitHub calls (short-lived in-process cache).
- Index and Ask are rate-limited. One active index job per user.
- Follow-up chat is persisted per snapshot. Re-index (new SHA) starts a new thread.
- No incremental re-index, webhooks, or multi-repo questions.
- Do not index huge monorepos; the job will fail the caps above.

Never put access tokens in prompts, logs, or vector metadata.
