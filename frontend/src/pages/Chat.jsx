import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { cancelIndex, getCachedRepos, getChats, getIndexStatus, getMe, getRepo, getThread, startChat, startIndex, streamAsk } from '../api.js';
import { isRateLimit, notify, notifyRateLimit } from '../notify.jsx';
import IndexingModal, { isIndexing } from './IndexingModal.jsx';

export default function Chat() {
    const { repoId } = useParams();
    const navigate = useNavigate();
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
    const inputRef = useRef(null);

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
        if (user && !user.hasGeminiKey) {
            setError('Add your Gemini API key in Profile before asking.');
            notify('Add your Gemini API key in Profile before asking.', 'warn');
            navigate('/settings?tab=keys');
            return;
        }
        setBusy(true);
        setError('');
        setQuestion('');
        if (inputRef.current) {
            inputRef.current.style.height = '';
        }
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
                    const nextCitations = data.citations || [];
                    setCitations(nextCitations);
                    setMessages((current) => {
                        const next = current.slice();
                        const last = next[next.length - 1] || {};
                        next[next.length - 1] = { ...last, role: 'ASSISTANT', citations: nextCitations };
                        return next;
                    });
                } else if (name === 'token') {
                    answer += data;
                    setMessages((current) => {
                        const next = current.slice();
                        const last = next[next.length - 1] || {};
                        next[next.length - 1] = { ...last, role: 'ASSISTANT', content: answer };
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
        if (user && !user.hasGeminiKey) {
            setError('Add your Gemini API key in Profile before indexing.');
            notify('Add your Gemini API key in Profile before indexing.', 'warn');
            navigate('/settings?tab=keys');
            return;
        }
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
    const lastBotIndex = lastAssistantIndex(messages);

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
                    <div className="ch-feed">
                    {user && !user.hasGeminiKey && (
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
                        <div className="ch-empty">
                            <span className="lp-dot" />
                            <p>Ask about this indexed snapshot.</p>
                        </div>
                    )}
                    {messages.map((message, index) => {
                        const live = busy && index === messages.length - 1;
                        const messageCitations = message.citations || (live ? citations : []);
                        if (message.role === 'USER') {
                            return (
                                <div key={index} className="ch-turn user">
                                    <div className="ch-bubble ch-bubble-user">{message.content}</div>
                                    <UserAvatar user={user} />
                                </div>
                            );
                        }
                        return (
                            <div key={index} className="ch-turn bot">
                                <span className={'ch-bot-mark' + ((live || index === lastBotIndex) ? ' live' : '')}>
                                    <i className="ch-bot-ring" aria-hidden="true" />
                                    <span className="lp-dot" />
                                </span>
                                <div className="ch-bubble ch-bubble-bot">
                                    <MessageBody
                                        text={message.content}
                                        pending={live}
                                        citations={messageCitations}
                                        repo={repo}
                                    />
                                </div>
                            </div>
                        );
                    })}
                    {error && <p className="hint">{error}</p>}
                    </div>
                </div>
                <div className="ch-dock">
                    <form className="ch-composer" onSubmit={onSubmit}>
                        <textarea
                            ref={inputRef}
                            rows={1}
                            value={question}
                            onChange={(event) => {
                                setQuestion(event.target.value);
                                event.target.style.height = 'auto';
                                event.target.style.height = Math.min(event.target.scrollHeight, 140) + 'px';
                            }}
                            onKeyDown={(event) => {
                                if (event.key === 'Enter' && !event.shiftKey) {
                                    event.preventDefault();
                                    onSubmit(event);
                                }
                            }}
                            maxLength={2000}
                            placeholder={ready ? 'Ask about this repository…' : 'Index this repository to start chatting'}
                            disabled={busy || !ready || (user && !user.hasGeminiKey)}
                        />
                        <button type="submit" disabled={busy || !ready || !question.trim() || (user && !user.hasGeminiKey)} aria-label="Send">
                            →
                        </button>
                    </form>
                    <p className="ch-disclaimer">GitGPT can make mistakes. Always verify important information.</p>
                </div>
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

function MessageBody({ text, pending, citations, repo }) {
    if (!text) {
        return pending ? <p className="ch-thinking" aria-live="polite">Thinking…</p> : null;
    }
    const normalized = rewriteCitations(text, citations);
    return (
        <div className={'ch-md' + (pending ? ' streaming' : '')}>
            {splitBlocks(normalized).map((block, index) => (
                block.type === 'code' ? (
                    <CodeBlock key={index} language={block.language} code={block.code} />
                ) : (
                    <MarkdownBlock key={index} text={block.text} citations={citations} repo={repo} />
                )
            ))}
        </div>
    );
}

function MarkdownBlock({ text, citations, repo }) {
    return parseMarkdownBlocks(text).map((block, index) => {
        if (block.type === 'heading') {
            const Tag = 'h' + block.level;
            return <Tag key={index}>{renderInline(block.text, citations, repo)}</Tag>;
        }
        if (block.type === 'ul') {
            return (
                <ul key={index}>
                    {block.items.map((item, itemIndex) => (
                        <li key={itemIndex}>{renderListItem(item, citations, repo)}</li>
                    ))}
                </ul>
            );
        }
        if (block.type === 'ol') {
            return (
                <ol key={index}>
                    {block.items.map((item, itemIndex) => (
                        <li key={itemIndex}>{renderInline(item, citations, repo)}</li>
                    ))}
                </ol>
            );
        }
        if (block.type === 'quote') {
            return <blockquote key={index}>{renderInline(block.text, citations, repo)}</blockquote>;
        }
        if (block.type === 'hr') {
            return <hr key={index} />;
        }
        return <p key={index}>{renderInline(block.text, citations, repo)}</p>;
    });
}

function parseMarkdownBlocks(text) {
    const lines = String(text).replace(/\r\n/g, '\n').split('\n');
    const blocks = [];
    let paragraph = [];
    let list = null;
    let quote = [];

    function flushParagraph() {
        const body = paragraph.join('\n').trim();
        if (body) {
            blocks.push({ type: 'p', text: body });
        }
        paragraph = [];
    }

    function flushList() {
        if (list && list.items.length) {
            blocks.push(list);
        }
        list = null;
    }

    function flushQuote() {
        if (quote.length) {
            blocks.push({ type: 'quote', text: quote.join('\n') });
            quote = [];
        }
    }

    for (const raw of lines) {
        const line = raw.replace(/\s+$/, '');
        const heading = /^(#{1,3})\s+(.+)$/.exec(line);
        const ul = /^\s*[-*+]\s+(.+)$/.exec(line);
        const ol = /^\s*(\d+)\.\s+(.+)$/.exec(line);
        const hr = /^ {0,3}(-{3,}|\*{3,}|_{3,})$/.exec(line);
        const bq = /^>\s?(.*)$/.exec(line);

        if (heading) {
            flushParagraph();
            flushList();
            flushQuote();
            blocks.push({ type: 'heading', level: heading[1].length, text: heading[2] });
            continue;
        }
        if (hr) {
            flushParagraph();
            flushList();
            flushQuote();
            blocks.push({ type: 'hr' });
            continue;
        }
        if (bq) {
            flushParagraph();
            flushList();
            quote.push(bq[1]);
            continue;
        }
        if (ul) {
            flushParagraph();
            flushQuote();
            if (!list || list.type !== 'ul') {
                flushList();
                list = { type: 'ul', items: [] };
            }
            list.items.push(ul[1]);
            continue;
        }
        if (isLabeledItem(line)) {
            flushParagraph();
            flushQuote();
            if (!list || list.type !== 'ul') {
                flushList();
                list = { type: 'ul', items: [] };
            }
            list.items.push(line);
            continue;
        }
        if (ol) {
            flushParagraph();
            flushQuote();
            if (!list || list.type !== 'ol') {
                flushList();
                list = { type: 'ol', items: [] };
            }
            list.items.push(ol[2]);
            continue;
        }
        if (!line.trim()) {
            flushParagraph();
            flushList();
            flushQuote();
            continue;
        }
        flushList();
        flushQuote();
        paragraph.push(line);
    }
    flushParagraph();
    flushList();
    flushQuote();
    return blocks;
}

function isLabeledItem(line) {
    if (/^\s*[-*+]\s+/.test(line) || /^\s*\d+\.\s+/.test(line) || /^#{1,3}\s+/.test(line) || /^>\s?/.test(line)) {
        return false;
    }
    const match = /^([^:]{2,80}):\s+\S/.exec(line);
    if (!match) {
        return false;
    }
    const label = match[1].trim();
    return label.length > 0 && label.length <= 70 && !/^\[/.test(label) && !/^https?:/.test(label);
}

function renderListItem(item, citations, repo) {
    const labeled = /^([^:]{2,80}):\s+(.+)$/.exec(item);
    if (!labeled) {
        return renderInline(item, citations, repo);
    }
    return (
        <>
            <strong>{renderInline(labeled[1].trim(), citations, repo)}</strong>
            {': '}
            {renderInline(labeled[2], citations, repo)}
        </>
    );
}

function rewriteCitations(text, citations) {
    const list = Array.isArray(citations) ? citations : [];
    return String(text).replace(/\[\s*([^\[\]]+?)\s*\]/g, (full, inner) => {
        const trimmed = inner.trim();
        if (/^\d+(?:\s*,\s*\d+)*$/.test(trimmed)) {
            return '[' + trimmed.split(',').map((part) => part.trim()).join(',') + ']';
        }
        const refs = parseCitationRefs('[' + trimmed + ']');
        if (refs.length === 0) {
            return full;
        }
        const nums = [...new Set(refs.map((ref) => indexForRef(ref, list)).filter(Boolean))];
        if (nums.length > 0) {
            return '[' + nums.join(',') + ']';
        }
        return '[' + refs.map((ref) => (
            ref.path + ':' + ref.startLine + (ref.endLine !== ref.startLine ? '-' + ref.endLine : '')
        )).join(', ') + ']';
    });
}

function renderInline(text, citations, repo) {
    const pattern = /(`[^`]+`)|(\*\*[^*]+?\*\*)|(\*[^*]+?\*)|(\[[^\]]+\]\([^)]+\))|(\[\s*\d+(?:\s*,\s*\d+)*\s*\])|(\[\s*(?!\d+[,\s\]])[^\]\n]+?:\d+(?:-\d+)?(?:\s*,\s*[^\]\n]+?:\d+(?:-\d+)?)*\s*\])|(https?:\/\/[^\s)]+)/g;
    const nodes = [];
    let last = 0;
    let key = 0;
    let match;
    while ((match = pattern.exec(text)) !== null) {
        if (match.index > last) {
            nodes.push(text.slice(last, match.index));
        }
        const token = match[0];
        if (token.startsWith('`')) {
            nodes.push(<code key={key++} className="ch-inline">{token.slice(1, -1)}</code>);
        } else if (token.startsWith('**')) {
            nodes.push(<strong key={key++}>{token.slice(2, -2)}</strong>);
        } else if (token.startsWith('*')) {
            nodes.push(<em key={key++}>{token.slice(1, -1)}</em>);
        } else if (token.startsWith('[') && token.includes('](')) {
            const link = /^\[([^\]]+)\]\(([^)]+)\)$/.exec(token);
            nodes.push(
                <a key={key++} href={link[2]} target="_blank" rel="noreferrer">{link[1]}</a>
            );
        } else if (token.startsWith('[')) {
            nodes.push(<CiteMark key={key++} token={token} citations={citations} repo={repo} />);
        } else {
            nodes.push(
                <a key={key++} href={token} target="_blank" rel="noreferrer">{token}</a>
            );
        }
        last = match.index + token.length;
    }
    if (last < text.length) {
        nodes.push(text.slice(last));
    }
    return nodes;
}

function CiteMark({ token, citations, repo }) {
    const items = resolveCiteItems(token, citations, repo);
    if (items.length === 0) {
        return token;
    }
    const label = items.map((item) => item.number).filter(Boolean).join(',') || '1';
    return (
        <span className="ch-cite-mark">
            <button type="button" className="ch-cite-ref" aria-label={'Citation ' + label}>
                [{label}]
            </button>
            <span className="ch-cite-pop" role="tooltip">
                {items.map((item, index) => (
                    item.href ? (
                        <a
                            key={index}
                            className="ch-cite-chip"
                            href={item.href}
                            target="_blank"
                            rel="noreferrer"
                            title={item.path}
                        >
                            {item.label}
                        </a>
                    ) : (
                        <span key={index} className="ch-cite-chip">{item.label}</span>
                    )
                ))}
            </span>
        </span>
    );
}

function resolveCiteItems(token, citations, repo) {
    const list = Array.isArray(citations) ? citations : [];
    if (/^\[\s*\d+(?:\s*,\s*\d+)*\s*\]$/.test(token)) {
        return parseNumberRefs(token).map((number) => {
            const citation = list[number - 1];
            if (!citation) {
                return { number, label: String(number), href: null, path: '' };
            }
            return citeItem(number, citation, repo);
        });
    }
    return parseCitationRefs(token).map((ref) => {
        const index = indexForRef(ref, list);
        return {
            number: index || 1,
            path: ref.path,
            label: chipLabel(ref.path, ref.startLine, ref.endLine),
            href: citationHref(ref, list, repo)
        };
    });
}

function parseNumberRefs(token) {
    return String(token).replace(/[\[\]]/g, '').split(',').map((part) => Number(part.trim())).filter((number) => number > 0);
}

function indexForRef(ref, citations) {
    const exact = citations.findIndex((item) => samePath(item.path, ref.path) && Number(item.startLine) === ref.startLine);
    if (exact >= 0) {
        return exact + 1;
    }
    const byPath = citations.findIndex((item) => samePath(item.path, ref.path));
    if (byPath >= 0) {
        return byPath + 1;
    }
    const name = fileName(ref.path);
    const byNameLine = citations.findIndex((item) => fileName(item.path) === name && Number(item.startLine) === ref.startLine);
    if (byNameLine >= 0) {
        return byNameLine + 1;
    }
    const byName = citations.findIndex((item) => fileName(item.path) === name);
    return byName >= 0 ? byName + 1 : 0;
}

function citeItem(number, citation, repo) {
    return {
        number,
        path: citation.path,
        label: chipLabel(citation.path, citation.startLine, citation.endLine),
        href: citation.githubUrl || githubBlobUrl(
            repo?.fullName,
            citation.commitSha || repo?.indexedSha,
            citation.path,
            citation.startLine,
            citation.endLine
        )
    };
}

function chipLabel(path, startLine, endLine) {
    const lines = startLine
        ? (endLine && endLine !== startLine ? startLine + '-' + endLine : String(startLine))
        : '';
    return fileName(path) + (lines ? ':' + lines : '');
}

function parseCitationRefs(token) {
    const inner = String(token).replace(/^\[/, '').replace(/\]$/, '');
    return inner.split(',').map((part) => {
        const match = /^\s*(.+):(\d+)(?:-(\d+))?\s*$/.exec(part);
        if (!match) {
            return null;
        }
        return {
            path: match[1].trim(),
            startLine: Number(match[2]),
            endLine: match[3] ? Number(match[3]) : Number(match[2])
        };
    }).filter(Boolean);
}

function citationHref(ref, citations, repo) {
    const list = Array.isArray(citations) ? citations : [];
    const exact = list.find((item) => samePath(item.path, ref.path) && Number(item.startLine) === ref.startLine);
    if (exact?.githubUrl) {
        return withLineHash(exact.githubUrl, ref.startLine, ref.endLine);
    }
    const byPath = list.find((item) => samePath(item.path, ref.path));
    if (byPath?.githubUrl) {
        return withLineHash(byPath.githubUrl, ref.startLine, ref.endLine);
    }
    return githubBlobUrl(repo?.fullName, byPath?.commitSha || repo?.indexedSha, ref.path, ref.startLine, ref.endLine);
}

function samePath(left, right) {
    const a = normalizePath(left);
    const b = normalizePath(right);
    return a === b || a.endsWith('/' + b) || b.endsWith('/' + a);
}

function normalizePath(path) {
    return String(path || '').replace(/\\/g, '/').replace(/^\.\//, '').replace(/^\/+/, '');
}

function withLineHash(url, startLine, endLine) {
    const base = String(url).replace(/#L\d+(?:-L\d+)?$/, '');
    if (!startLine) {
        return base;
    }
    return base + '#L' + startLine + (endLine > startLine ? '-L' + endLine : '');
}

function githubBlobUrl(fullName, commitSha, path, startLine, endLine) {
    if (!fullName || !commitSha || !path) {
        return null;
    }
    const encoded = normalizePath(path).split('/').map(encodeURIComponent).join('/');
    return withLineHash('https://github.com/' + fullName + '/blob/' + commitSha + '/' + encoded, startLine, endLine);
}

function UserAvatar({ user }) {
    const initial = (user?.githubUsername || 'U').slice(0, 1).toUpperCase();
    return (
        <span className="ch-user-avatar">
            {user?.urlAvatar ? <img src={user.urlAvatar} alt="" /> : initial}
        </span>
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

function lastAssistantIndex(messages) {
    return messages.reduce((acc, message, index) => (
        message.role === 'USER' ? acc : index
    ), -1);
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
