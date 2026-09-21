import { useEffect, useRef, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { cancelIndex, getCachedRepos, getChats, getIndexStatus, getMe, getRepo, getThread, startChat, startIndex, streamAsk } from '../api.js';
import { isRateLimit, notify, notifyRateLimit } from '../notify.jsx';
import IndexingModal, { isIndexing } from './IndexingModal.jsx';

export default function Chat() {
    const { repoId } = useParams();
    const [params, setParams] = useSearchParams();
    const sessionId = params.get('session');
    const [repo, setRepo] = useState(null);
    const [repos, setRepos] = useState([]);
    const [chats, setChats] = useState([]);
    const [question, setQuestion] = useState('');
    const [messages, setMessages] = useState([]);
    const [citations, setCitations] = useState([]);
    const [showCitations, setShowCitations] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [copied, setCopied] = useState(false);
    const [indexJob, setIndexJob] = useState(null);
    const [user, setUser] = useState(null);
    const threadRef = useRef(null);

    useEffect(() => {
        getMe().then((me) => {
            if (!me) {
                window.location.href = '/';
                return;
            }
            setUser(me);
        }).catch(() => {
            window.location.href = '/';
        });
    }, []);

    useEffect(() => {
        getRepo(repoId).then(setRepo).catch(() => {});
        getCachedRepos().then(setRepos).catch(() => {});
        getChats(repoId).then(setChats).catch(() => {});
        getThread(repoId, sessionId).then((data) => {
            setMessages(data.messages || []);
            const lastCited = [...(data.messages || [])].reverse()
                .find((message) => message.citations && message.citations.length > 0);
            setCitations(lastCited ? lastCited.citations : []);
            if (data.sessionId && data.sessionId !== sessionId) {
                setParams({ session: data.sessionId }, { replace: true });
            }
        }).catch((ex) => setError(ex.message));
    }, [repoId, sessionId]);

    useEffect(() => {
        if (!isIndexing(repo?.indexStatus) && !(indexJob && (isIndexing(indexJob.status) || indexJob.cancelRequested))) {
            return undefined;
        }
        let closed = false;
        async function poll() {
            try {
                const status = await getIndexStatus(repoId);
                setIndexJob(status);
                setRepo((current) => current ? { ...current, indexStatus: status.status, indexError: status.errorMessage } : current);
                if (status.status === 'READY' && !closed) {
                    closed = true;
                    setTimeout(() => setIndexJob(null), 800);
                }
            } catch {
                // keep polling
            }
        }
        poll();
        const timer = setInterval(poll, 1500);
        return () => clearInterval(timer);
    }, [repoId, repo?.indexStatus, indexJob?.status, indexJob?.cancelRequested]);

    useEffect(() => {
        if (threadRef.current) {
            threadRef.current.scrollTop = threadRef.current.scrollHeight;
        }
    }, [messages, busy]);

    async function onSubmit(event) {
        event.preventDefault();
        const text = question.trim();
        if (!text || busy) {
            return;
        }
        setBusy(true);
        setError('');
        setQuestion('');
        setShowCitations(true);
        setMessages((current) => [
            ...current,
            { role: 'USER', content: text },
            { role: 'ASSISTANT', content: '' }
        ]);
        let answer = '';
        try {
            await streamAsk(repoId, sessionId, text, (name, data) => {
                if (name === 'meta') {
                    if (data.sessionId) {
                        setParams({ session: data.sessionId }, { replace: true });
                    }
                    setCitations(data.citations || []);
                } else if (name === 'token') {
                    answer += data;
                    setMessages((current) => {
                        const next = current.slice();
                        next[next.length - 1] = { role: 'ASSISTANT', content: answer };
                        return next;
                    });
                } else if (name === 'error' || name === 'fail') {
                    const message = typeof data === 'string' ? data : (data && data.message) || 'Ask failed.';
                    setError(message);
                }
            });
            if (!answer.trim()) {
                throw new Error('The model returned no text. Try again or pick another model in Settings.');
            }
            getChats(repoId).then(setChats).catch(() => {});
        } catch (ex) {
            setError(ex.message);
            if (isRateLimit(ex)) {
                notifyRateLimit(ex.message);
            } else {
                notify(ex.message || 'Ask failed.', 'error');
            }
        } finally {
            setBusy(false);
        }
    }

    async function onNewChat() {
        const created = await startChat(repoId);
        setMessages([]);
        setCitations([]);
        setError('');
        setParams({ session: created.sessionId });
    }

    async function onReindex() {
        setIndexJob({ status: 'QUEUED', progressStep: 'CLONE', progressPercent: 8 });
        setRepo((current) => current ? { ...current, indexStatus: 'QUEUED' } : current);
        try {
            const job = await startIndex(repoId);
            setIndexJob(job);
        } catch (ex) {
            setError(ex.message);
            setIndexJob({ status: 'FAILED', errorMessage: ex.message });
            if (isRateLimit(ex)) {
                notifyRateLimit(ex.message);
            }
        }
    }

    async function onCancelIndex() {
        if (indexJob?.status === 'FAILED') {
            setIndexJob(null);
            return;
        }
        const job = await cancelIndex(repoId);
        setIndexJob(job);
    }

    async function onShare() {
        await navigator.clipboard.writeText(window.location.href);
        setCopied(true);
        setTimeout(() => setCopied(false), 1600);
    }

    const repoName = shortName(repo?.fullName || repo?.name);
    const ready = repo?.indexStatus === 'READY';
    const indexing = repo?.indexStatus === 'QUEUED' || repo?.indexStatus === 'RUNNING';
    const otherRepos = repos.filter((item) => item.repoId !== repoId && item.indexStatus === 'READY');

    return (
        <div className={'ch' + (showCitations ? '' : ' no-cite')}>
            <header className="ch-top">
                <Link className="hm-brand" to="/home">
                    <span className="lp-dot" />
                    GITGPT
                </Link>
                <div className="ch-crumb">
                    <Link to="/home">‹</Link>
                    <strong>{repoName}</strong>
                </div>
                <div className="ch-top-actions">
                    <Link to="/settings">Profile</Link>
                    <button type="button" onClick={onShare}>{copied ? 'Copied' : 'Share'}</button>
                    <button
                        type="button"
                        className={showCitations ? 'on' : ''}
                        onClick={() => setShowCitations((open) => !open)}
                    >
                        Citations ({citations.length})
                    </button>
                </div>
            </header>

            <aside className="ch-side">
                <p className="ch-repo">{repoName}</p>
                <button className="ch-new" type="button" onClick={onNewChat}>+ New chat</button>
                <div className="ch-chats">
                    {chats.map((chat) => (
                        <Link
                            key={chat.sessionId}
                            className={'ch-item' + (chat.sessionId === sessionId ? ' on' : '')}
                            to={`/repos/${repoId}?session=${chat.sessionId}`}
                        >
                            {chat.title || 'Conversation'}
                        </Link>
                    ))}
                </div>
                {otherRepos.length > 0 && (
                    <div className="ch-other">
                        {otherRepos.map((item) => (
                            <Link key={item.repoId} className="ch-item muted" to={'/repos/' + item.repoId}>
                                {shortName(item.fullName)}
                            </Link>
                        ))}
                    </div>
                )}
                <div className="ch-status">
                    <span className={ready ? 'hm-pill on' : indexing ? 'hm-pill busy' : 'hm-pill'}>
                        <i /> {ready ? 'Indexed' : indexing ? 'Indexing…' : (repo?.indexStatus || 'Not indexed')}
                    </span>
                    <p>Last updated {ago(repo?.indexedAt || repo?.githubPushedAt)}</p>
                    <button type="button" disabled={indexing} onClick={onReindex}>Re-index</button>
                </div>
            </aside>

            <section className="ch-main">
                <div className="ch-thread" ref={threadRef}>
                    {user && !user.hasGeminiKey && !user.hasServerGeminiKey && (
                        <div className="st-banner">
                            <div>
                                <strong>Add your Gemini API key</strong>
                                <p>Questions and re-indexing need a key from Google AI Studio.</p>
                            </div>
                            <Link to="/settings?tab=keys">Open Profile</Link>
                        </div>
                    )}
                    {ready && user && repo?.indexEmbeddingModel && user.embeddingModel
                        && repo.indexEmbeddingModel !== user.embeddingModel && (
                        <div className="st-banner warn">
                            <div>
                                <strong>Embedding model changed</strong>
                                <p>This repo was indexed with {repo.indexEmbeddingModel}. Re-index to use {user.embeddingModel}.</p>
                            </div>
                        </div>
                    )}
                    {messages.length === 0 && (
                        <p className="ch-empty">Ask about this indexed snapshot.</p>
                    )}
                    {messages.map((message, index) => (
                        message.role === 'USER' ? (
                            <div key={index} className="ch-user">{message.content}</div>
                        ) : (
                            <article key={index} className="ch-bot">
                                <span className="lp-dot" />
                                <div className="ch-bot-body">
                                    <MessageBody text={message.content} pending={busy && index === messages.length - 1} />
                                </div>
                            </article>
                        )
                    ))}
                    {error && <p className="hint">{error}</p>}
                </div>
                <form className="ch-composer" onSubmit={onSubmit}>
                    <input
                        value={question}
                        onChange={(event) => setQuestion(event.target.value)}
                        maxLength={2000}
                        placeholder="Ask about this repository…"
                        disabled={busy || !ready}
                    />
                    <button type="submit" disabled={busy || !ready || !question.trim()} aria-label="Send">
                        →
                    </button>
                </form>
                <p className="ch-disclaimer">GitGPT can make mistakes. Always verify important information.</p>
            </section>

            {showCitations && (
                <aside className="ch-cite">
                    <div className="ch-cite-head">
                        <strong>Citations ({citations.length})</strong>
                        <button type="button" onClick={() => setShowCitations(false)} aria-label="Close citations">×</button>
                    </div>
                    {citations.length === 0 && (
                        <p className="hint">Cited files will open on GitHub at the exact lines.</p>
                    )}
                    <ul className="ch-cite-list">
                        {citations.map((citation, index) => (
                            <li key={index} className="ch-cite-card">
                                <div className="ch-cite-title">
                                    <FileIcon />
                                    <strong>{fileName(citation.path)}</strong>
                                </div>
                                <p>{citation.path}</p>
                                <p className="ch-cite-lines">L{citation.startLine}-{citation.endLine}</p>
                                {citation.githubUrl ? (
                                    <a href={citation.githubUrl} target="_blank" rel="noreferrer">View on GitHub →</a>
                                ) : (
                                    <span className="hint">No GitHub link</span>
                                )}
                            </li>
                        ))}
                    </ul>
                </aside>
            )}
            {indexJob && (isIndexing(indexJob.status) || String(indexJob.status || '').toUpperCase() === 'FAILED' || String(indexJob.status || '').toUpperCase() === 'READY') && (
                <IndexingModal repoName={repoName} job={indexJob} onCancel={onCancelIndex} />
            )}
        </div>
    );
}

function MessageBody({ text, pending }) {
    if (!text) {
        return <p className="hint">{pending ? 'Thinking…' : ''}</p>;
    }
    return (
        <div className="ch-md">
            {splitBlocks(text).map((block, index) => (
                block.type === 'code' ? (
                    <CodeBlock key={index} language={block.language} code={block.code} />
                ) : (
                    <p key={index}>{block.text}</p>
                )
            ))}
        </div>
    );
}

function CodeBlock({ language, code }) {
    const [copied, setCopied] = useState(false);
    return (
        <div className="ch-code">
            <div className="ch-code-bar">
                <span>{language || 'code'}</span>
                <button
                    type="button"
                    onClick={async () => {
                        await navigator.clipboard.writeText(code);
                        setCopied(true);
                        setTimeout(() => setCopied(false), 1200);
                    }}
                >
                    {copied ? 'Copied' : 'Copy'}
                </button>
            </div>
            <pre><code>{code}</code></pre>
        </div>
    );
}

function splitBlocks(text) {
    const parts = [];
    const chunks = String(text).split(/```/);
    chunks.forEach((chunk, index) => {
        if (index % 2 === 1) {
            const newline = chunk.indexOf('\n');
            const language = newline === -1 ? '' : chunk.slice(0, newline).trim();
            const code = newline === -1 ? chunk : chunk.slice(newline + 1);
            parts.push({ type: 'code', language, code: code.replace(/\n$/, '') });
        } else if (chunk.trim()) {
            parts.push({ type: 'text', text: chunk.trim() });
        }
    });
    return parts;
}

function shortName(fullName) {
    if (!fullName) {
        return 'repository';
    }
    const parts = fullName.split('/');
    return parts[parts.length - 1] || fullName;
}

function fileName(path) {
    if (!path) {
        return 'file';
    }
    const parts = path.split('/');
    return parts[parts.length - 1] || path;
}

function ago(value) {
    if (!value) {
        return 'recently';
    }
    const ms = Date.now() - Date.parse(value);
    const days = Math.max(0, Math.floor(ms / 86400000));
    if (days <= 0) {
        return 'today';
    }
    if (days === 1) {
        return '1 day ago';
    }
    if (days < 30) {
        return days + ' days ago';
    }
    return Math.floor(days / 30) + ' months ago';
}

function FileIcon() {
    return (
        <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
            <path d="M4 2.5h5.2L12 5.3V13.5H4Z" fill="none" stroke="currentColor" strokeWidth="1.3" />
            <path d="M9.2 2.5V5.3H12" fill="none" stroke="currentColor" strokeWidth="1.3" />
        </svg>
    );
}
