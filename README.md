# GitGPT

GitGPT lets you sign in with GitHub, pick **one repository**, index it, and ask questions about that snapshot. Answers are grounded in retrieved code chunks, not a live crawl of GitHub.

## What it does

1. **GitHub login** — OAuth with `read:user`, `user:email`, and `repo`.
2. **Repo list** — the React home page loads cached repos immediately, then one GitHub page at a time. Listing does **not** embed anything.
3. **Index** — you choose a repo. An async job snapshots the default branch (commit SHA), filters files, chunks them, and stores embeddings in Postgres/pgvector.
4. **Ask** — only when status is `READY`. Retrieval is: optional query plan → keyword/path candidates ∥ vector search (scoped to you + that repo + SHA) → pack a few chunks → Gemini. The page is a persisted thread: each turn retrieves again; history is only for follow-ups. The answer streams over SSE.

Index status: `NOT_INDEXED` → `QUEUED` → `RUNNING` → `READY` | `FAILED`.

## Stack

- Java 17, Spring Boot 4.1, Spring Security OAuth2 (API only)
- React + Vite UI in `frontend/`
- PostgreSQL + [pgvector](https://github.com/pgvector/pgvector)
- Spring AI 2 with Google Gemini (`gemini-3.5-flash-lite` chat, `gemini-embedding-001` at 1536 dimensions)

Packages:

- `com.genai.gitgpt.user` — OAuth, users, repo catalog
- `com.genai.gitgpt.rag` — index jobs, ingest, retrieve, ask

## Prerequisites

- JDK 17+
- PostgreSQL with the `vector` extension enabled in your schema
- A [GitHub OAuth App](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/creating-an-oauth-app)
  - Homepage URL: `http://localhost:5173`
  - Authorization callback URL: `http://localhost:5173/login/oauth2/code/github`
- A [Google AI Studio](https://aistudio.google.com/apikey) API key (each signed-in user adds their own in Settings; `GOOGLE_API_KEY` is an optional server fallback)

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
| `GOOGLE_API_KEY` | Optional server Gemini key. If unset, each user must add their own key in Settings. |
| `GITHUB_CLIENT_ID` | GitHub OAuth client id |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth client secret |
| `GITGPT_TOKEN_ENCRYPTION_KEY` | Token encryption key. Local fallback is `gitgpt-local-dev-only-not-for-prod`. Best for real use: `openssl rand -base64 32`. |

Index caps (see `application.properties`): at most 400 files, 256 KB per file, 8 MB total text. Embeddings use 1536 dimensions because pgvector HNSW tops out at 2000 (Gemini’s default is 3072).

## Run

```bash
./mvnw spring-boot:run
```

In another terminal:

```bash
cd frontend
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173). The API is [http://localhost:8080](http://localhost:8080). Vite proxies `/api`, `/oauth2`, `/login`, and `/logout` so the session cookie stays on the UI origin.

**Change the GitHub OAuth callback** from `:8080` to `http://localhost:5173/login/oauth2/code/github`. After login, Spring redirects to `/home` on the React app.

```bash
./mvnw test
```

## Docker (host it)

Frontend (nginx) and backend (Spring) run as containers. Nginx serves the React app and proxies `/api`, `/oauth2`, `/login`, and `/logout` to Spring, so the session cookie stays on one public origin — same pattern as local Vite.

```bash
cp .env.example .env
cp .env.backend.example .env.backend
cp frontend/.env.example frontend/.env
# fill GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET, GITGPT_TOKEN_ENCRYPTION_KEY
# set DB_PASSWORD and POSTGRES_PASSWORD to the same value
# FRONTEND_ORIGIN = public URL, no trailing slash
docker compose up --build
```

Open [http://localhost](http://localhost) (`HTTP_PORT` in `.env`, default 80).

| File | Used by |
|---|---|
| `.env` | Compose port mapping (`HTTP_PORT`) |
| `.env.backend` | Postgres + Spring (DB, GitHub OAuth, CORS origin, encryption key) |
| `frontend/.env` | Browser API URL (`GITGPT_API_BASE_URL`), Vite proxy, nginx `BACKEND_UPSTREAM` |

Point the GitHub OAuth App at that origin:

- Homepage URL: `{FRONTEND_ORIGIN}`
- Authorization callback URL: `{FRONTEND_ORIGIN}/login/oauth2/code/github`

Leave `GITGPT_API_BASE_URL` empty when nginx (or Vite) proxies `/api` on the same origin.

If you host the UI and API on **different URLs**, set:

```
# frontend/.env
GITGPT_API_BASE_URL=https://api.your.domain

# .env.backend
FRONTEND_ORIGIN=https://ui.your.domain
GITGPT_OAUTH_REDIRECT_URI=https://api.your.domain/login/oauth2/code/github
COOKIE_SAME_SITE=none
COOKIE_SECURE=true
```

`GITGPT_API_BASE_URL` is written into `/config.js` when the frontend container starts, so you can change the API host without rebuilding the image.

For HTTPS in front of a single compose stack, set `FRONTEND_ORIGIN=https://your.domain` and keep `GITGPT_API_BASE_URL` empty. Users still add their own Gemini key in Settings after sign-in.

## Ask pipeline

1. If the question already contains identifiers (`UserService`, `Foo.java`), skip the LLM planner.
2. Keyword/path search and vector search run **in parallel**, filtered by `userId`, `repoId`, and `commitSha`.
3. Overlapping windows are dropped; about 5 chunks are sent to the model.
4. The UI streams tokens (`POST /api/repos/{repoId}/ask/stream`). Citations come from retrieval, not from guessing.
5. Follow-ups stay in the same `(user, repo, snapshot)` session. Retrieval still runs every turn.

The model is instructed to answer only from those chunks.

## HTTP (API)

| Method | Path | Notes |
|---|---|---|
| GET | `/guide` | React how-it-works guide (frontend) |
| GET | `/api/me` | Current user + Gemini key/model flags |
| GET/PUT/DELETE | `/api/me/gemini` | Encrypted Gemini key + chat/embedding model |
| GET | `/api/csrf` | CSRF cookie token |
| GET | `/api/repos` | Cached repos only (fast) |
| GET | `/api/repos/github?page=` | One GitHub page |
| GET | `/api/repos/{repoId}` | One repo |
| GET | `/api/chats` | Past conversations |
| GET | `/api/repos/{repoId}/chats` | Threads for one repo |
| POST | `/api/repos/{repoId}/chats` | New thread |
| GET/POST | `/api/repos/{repoId}/index` | Status / start |
| POST | `/api/repos/{repoId}/ask` | JSON ask |
| POST | `/api/repos/{repoId}/ask/stream` | SSE ask |
| GET | `/api/repos/{repoId}/chat` | Thread messages |

## Limits (current)

- GitHub access tokens and user Gemini API keys are AES-GCM encrypted at rest. Decrypt only in memory (short-lived in-process cache). Never log keys.
- Users pick a Gemini chat model and embedding model. Embeddings stay at 1536 dimensions; re-index after changing the embedding model.
- Index and Ask are rate-limited. One active index job per user.
- Follow-up chat is persisted per snapshot. Re-index (new SHA) starts a new thread.
- No incremental re-index, webhooks, or multi-repo questions.
- Do not index huge monorepos; the job will fail the caps above.

Never put access tokens in prompts, logs, or vector metadata.
