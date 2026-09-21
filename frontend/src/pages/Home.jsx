import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { cancelIndex, getCachedRepos, getChats, getGithubRepos, getIndexStatus, getMe, startIndex } from '../api.js';
import { isRateLimit, notify, notifyRateLimit } from '../notify.jsx';
import IndexingModal, { isIndexing } from './IndexingModal.jsx';
import UserMenu from './UserMenu.jsx';

export default function Home() {
    const navigate = useNavigate();
    const [user, setUser] = useState(null);
    const [repos, setRepos] = useState([]);
    const [chats, setChats] = useState([]);
    const [page, setPage] = useState(0);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [repoQuery, setRepoQuery] = useState('');
    const [chatQuery, setChatQuery] = useState('');
    const [filter, setFilter] = useState('all');
    const [openGroups, setOpenGroups] = useState({});
    const [indexJob, setIndexJob] = useState(null);
    const [indexRepoId, setIndexRepoId] = useState(null);
    const rateToast = useRef('');

    useEffect(() => {
        getMe().then((me) => {
            if (!me) {
                window.location.href = '/';
                return;
            }
            setUser(me);
            getCachedRepos().then(setRepos).catch(() => {});
            getChats().then((items) => {
                setChats(items);
                const groups = {};
                items.forEach((chat) => {
                    groups[chat.repoFullName] = true;
                });
                setOpenGroups(groups);
            }).catch(() => {});
            loadGithub(1);
        }).catch(() => {
            window.location.href = '/';
        });
    }, []);

    const indexingId = repos.find((repo) => isIndexing(repo.indexStatus))?.repoId;

    useEffect(() => {
        const focusId = indexRepoId || indexingId;
        if (!focusId) {
            return undefined;
        }
        if (!indexRepoId) {
            setIndexRepoId(focusId);
        }
        let closed = false;
        async function poll() {
            try {
                const status = await getIndexStatus(focusId);
                setIndexJob(status);
                setRepos((current) => current.map((item) => (
                    item.repoId === focusId
                        ? { ...item, indexStatus: status.status, indexError: status.errorMessage }
                        : item
                )));
                if (status.status === 'FAILED' && isRateLimit(status) && rateToast.current !== status.errorMessage) {
                    rateToast.current = status.errorMessage || 'rate';
                    notifyRateLimit(status.errorMessage);
                }
                if (status.status === 'READY' && !closed) {
                    closed = true;
                    setTimeout(() => {
                        setIndexJob(null);
                        setIndexRepoId(null);
                    }, 800);
                }
            } catch {
                // keep polling
            }
        }
        poll();
        const timer = setInterval(poll, 1500);
        return () => clearInterval(timer);
    }, [indexRepoId, indexingId]);

    async function loadGithub(nextPage) {
        setLoading(true);
        setError('');
        try {
            const batch = await getGithubRepos(nextPage);
            setPage(nextPage);
            setRepos((current) => mergeRepos(current, batch));
        } catch (ex) {
            setError(ex.message);
            if (isRateLimit(ex)) {
                notifyRateLimit(ex.message);
            }
        } finally {
            setLoading(false);
        }
    }

    async function indexRepo(repoId) {
        if (needsGeminiKey(user)) {
            setError('Add your Gemini API key in Profile before indexing.');
            notify('Add your Gemini API key in Profile before indexing.', 'warn');
            navigate('/settings?tab=keys');
            return;
        }
        setError('');
        setIndexRepoId(repoId);
        setIndexJob({ status: 'QUEUED', progressStep: 'CLONE', progressPercent: 8 });
        setRepos((current) => current.map((repo) => (
            repo.repoId === repoId ? { ...repo, indexStatus: 'QUEUED' } : repo
        )));
        try {
            const job = await startIndex(repoId);
            setIndexJob(job);
            setRepos((current) => current.map((repo) => (
                repo.repoId === repoId ? { ...repo, indexStatus: job.status || 'QUEUED' } : repo
            )));
        } catch (ex) {
            setError(ex.message);
            setIndexJob({ status: 'FAILED', errorMessage: ex.message });
            setRepos((current) => current.map((repo) => (
                repo.repoId === repoId ? { ...repo, indexStatus: 'NOT_INDEXED' } : repo
            )));
            if (isRateLimit(ex)) {
                notifyRateLimit(ex.message);
            } else {
                notify(ex.message, 'error');
            }
        }
    }

    async function onCancelIndex() {
        if (!indexRepoId) {
            setIndexJob(null);
            return;
        }
        if (indexJob?.status === 'FAILED') {
            setIndexJob(null);
            setIndexRepoId(null);
            return;
        }
        const job = await cancelIndex(indexRepoId);
        setIndexJob(job);
    }

    const indexedCount = repos.filter((repo) => repo.indexStatus === 'READY').length;
    const notIndexedCount = repos.length - indexedCount;

    const visibleRepos = useMemo(() => {
        const q = repoQuery.trim().toLowerCase();
        return repos
            .filter((repo) => {
                if (filter === 'indexed') {
                    return repo.indexStatus === 'READY';
                }
                if (filter === 'not') {
                    return repo.indexStatus !== 'READY';
                }
                return true;
            })
            .filter((repo) => {
                if (!q) {
                    return true;
                }
                return (repo.fullName || '').toLowerCase().includes(q)
                    || (repo.description || '').toLowerCase().includes(q)
                    || (repo.language || '').toLowerCase().includes(q);
            })
            .sort((a, b) => {
                const rank = (repo) => {
                    if (repo.indexStatus === 'READY') {
                        return 0;
                    }
                    if (isIndexing(repo.indexStatus)) {
                        return 1;
                    }
                    return 2;
                };
                const byStatus = rank(a) - rank(b);
                return byStatus !== 0 ? byStatus : timeOf(b) - timeOf(a);
            });
    }, [repos, repoQuery, filter]);

    const groupedChats = useMemo(() => {
        const q = chatQuery.trim().toLowerCase();
        const groups = new Map();
        chats.forEach((chat) => {
            if (q && !(chat.title || '').toLowerCase().includes(q) && !(chat.repoFullName || '').toLowerCase().includes(q)) {
                return;
            }
            const key = chat.repoFullName || 'Repository';
            if (!groups.has(key)) {
                groups.set(key, []);
            }
            groups.get(key).push(chat);
        });
        return groups;
    }, [chats, chatQuery]);

    if (!user) {
        return <div className="hm hm-boot">Loading…</div>;
    }

    return (
        <div className="hm">
            <header className="hm-top">
                <Link className="hm-brand" to="/home">
                    <span className="lp-dot" />
                    GITGPT
                </Link>
                <label className="hm-search">
                    <SearchIcon />
                    <input
                        value={repoQuery}
                        onChange={(event) => setRepoQuery(event.target.value)}
                        placeholder="Search repositories…"
                    />
                </label>
                <UserMenu user={user} />
            </header>

            <div className="hm-body">
                <aside className="hm-side">
                    <Link className="hm-nav active" to="/home">
                        <HomeIcon /> Home
                    </Link>
                    <Link className="hm-nav" to="/settings">
                        <GearIcon /> Profile
                    </Link>
                    <p className="hm-side-label">Your conversations</p>
                    <label className="hm-mini-search">
                        <SearchIcon />
                        <input
                            value={chatQuery}
                            onChange={(event) => setChatQuery(event.target.value)}
                            placeholder="Search conversations…"
                        />
                    </label>
                    {groupedChats.size === 0 && <p className="hint">No conversations yet.</p>}
                    {[...groupedChats.entries()].map(([repoName, items]) => (
                        <div key={repoName} className="hm-group">
                            <button
                                type="button"
                                className="hm-group-title"
                                onClick={() => setOpenGroups((current) => ({ ...current, [repoName]: !current[repoName] }))}
                            >
                                <span className="hm-caret">{openGroups[repoName] === false ? '▸' : '▾'}</span>
                                {shortName(repoName)}
                            </button>
                            {openGroups[repoName] !== false && items.map((chat, index) => (
                                <Link
                                    key={chat.sessionId}
                                    className="hm-chat"
                                    to={`/repos/${chat.repoId}?session=${chat.sessionId}`}
                                >
                                    {chat.title || ('Conversation ' + (index + 1))}
                                </Link>
                            ))}
                        </div>
                    ))}
                </aside>

                <section className="hm-main">
                    {needsGeminiKey(user) && (
                        <div className="st-banner">
                            <div>
                                <strong>Add your Gemini API key</strong>
                                <p>GitGPT uses your key to index repositories and answer questions.</p>
                            </div>
                            <Link to="/settings?tab=keys">Open Profile</Link>
                        </div>
                    )}
                    <div className="hm-heading">
                        <div>
                            <h1>Your Repositories</h1>
                            <p>Select a repository to start chatting with your code.</p>
                        </div>
                        <button className="hm-add" type="button" disabled={loading} onClick={() => loadGithub(page + 1 || 1)}>
                            <PlusIcon /> {loading ? 'Loading…' : 'Add repository'}
                        </button>
                    </div>

                    <div className="hm-toolbar">
                        <div className="hm-tabs">
                            <button className={filter === 'all' ? 'on' : ''} type="button" onClick={() => setFilter('all')}>
                                All Repositories <b>{repos.length}</b>
                            </button>
                            <button className={filter === 'indexed' ? 'on' : ''} type="button" onClick={() => setFilter('indexed')}>
                                Indexed <b>{indexedCount}</b>
                            </button>
                            <button className={filter === 'not' ? 'on' : ''} type="button" onClick={() => setFilter('not')}>
                                Not Indexed <b>{notIndexedCount}</b>
                            </button>
                        </div>
                        <span className="hm-sort">Indexed first, then recently updated</span>
                    </div>

                    {error && <p className="hint">{error}</p>}
                    {loading && repos.length === 0 && <p className="spinner">Loading repositories from GitHub…</p>}

                    <div className="hm-grid">
                        {visibleRepos.map((repo) => {
                            const ready = repo.indexStatus === 'READY';
                            const indexing = repo.indexStatus === 'QUEUED' || repo.indexStatus === 'RUNNING';
                            return (
                                <article key={repo.repoId} className="hm-card">
                                    <div className="hm-card-top">
                                        <h3>{shortName(repo.fullName)}</h3>
                                        <span className={ready ? 'hm-pill on' : indexing ? 'hm-pill busy' : 'hm-pill'}>
                                            <i /> {ready ? 'Indexed' : indexing ? 'Indexing…' : 'Not indexed'}
                                        </span>
                                    </div>
                                    <p>{repo.description || 'No description'}</p>
                                    <div className="hm-tags">
                                        {repo.language && <span>{repo.language}</span>}
                                        {repo.privateRepo && <span>Private</span>}
                                        {ready && repo.indexFileCount != null && <span>{repo.indexFileCount} files</span>}
                                    </div>
                                    <div className="hm-meta">
                                        <span><StarIcon /> {repo.starCount ?? 0}</span>
                                        <span><ForkIcon /> {repo.forkCount ?? 0}</span>
                                        <span>Updated {ago(repo.githubPushedAt || repo.indexedAt)}</span>
                                    </div>
                                    {ready ? (
                                        <button className="hm-open" type="button" onClick={() => navigate('/repos/' + repo.repoId)}>
                                            Open Chat →
                                        </button>
                                    ) : (
                                        <button
                                            className="hm-index"
                                            type="button"
                                            disabled={indexing}
                                            onClick={() => indexRepo(repo.repoId)}
                                        >
                                            {indexing ? 'Indexing…' : 'Index Repository'}
                                        </button>
                                    )}
                                </article>
                            );
                        })}
                    </div>
                </section>
            </div>
            {indexJob && (isIndexing(indexJob.status) || String(indexJob.status || '').toUpperCase() === 'FAILED' || String(indexJob.status || '').toUpperCase() === 'READY') && (
                <IndexingModal
                    repoName={shortName(repos.find((repo) => repo.repoId === indexRepoId)?.fullName)}
                    job={indexJob}
                    onCancel={onCancelIndex}
                />
            )}
        </div>
    );
}

function needsGeminiKey(user) {
    return user && !user.hasGeminiKey && !user.hasServerGeminiKey;
}

function mergeRepos(current, incoming) {
    const byId = new Map(current.map((repo) => [repo.repoId, repo]));
    incoming.forEach((repo) => byId.set(repo.repoId, repo));
    return Array.from(byId.values());
}

function shortName(fullName) {
    if (!fullName) {
        return 'repository';
    }
    const parts = fullName.split('/');
    return parts[parts.length - 1] || fullName;
}

function timeOf(repo) {
    const value = repo.githubPushedAt || repo.indexedAt || repo.updatedAt;
    return value ? Date.parse(value) : 0;
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

function SearchIcon() {
    return (
        <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
            <circle cx="7" cy="7" r="4.5" fill="none" stroke="currentColor" strokeWidth="1.4" />
            <path d="M10.5 10.5 14 14" fill="none" stroke="currentColor" strokeWidth="1.4" />
        </svg>
    );
}

function HomeIcon() {
    return (
        <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
            <path d="M2 8 8 2.5 14 8V14H9.5V10H6.5v4H2Z" fill="none" stroke="currentColor" strokeWidth="1.3" />
        </svg>
    );
}

function GearIcon() {
    return (
        <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
            <circle cx="8" cy="8" r="2.2" fill="none" stroke="currentColor" strokeWidth="1.3" />
            <path d="M8 2.2v1.6M8 12.2v1.6M2.2 8h1.6M12.2 8h1.6M3.8 3.8l1.1 1.1M11.1 11.1l1.1 1.1M12.2 3.8l-1.1 1.1M4.9 11.1 3.8 12.2" fill="none" stroke="currentColor" strokeWidth="1.3" />
        </svg>
    );
}

function PlusIcon() {
    return (
        <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
            <path d="M8 3v10M3 8h10" fill="none" stroke="currentColor" strokeWidth="1.6" />
        </svg>
    );
}

function StarIcon() {
    return (
        <svg viewBox="0 0 16 16" width="12" height="12" aria-hidden="true">
            <path d="M8 2.4 9.7 6l3.8.3-2.9 2.5.9 3.7L8 10.6 4.5 12.5l.9-3.7L2.5 6.3 6.3 6Z" fill="none" stroke="currentColor" strokeWidth="1.2" />
        </svg>
    );
}

function ForkIcon() {
    return (
        <svg viewBox="0 0 16 16" width="12" height="12" aria-hidden="true">
            <path d="M5 3.5v5.2c0 1.3 1.2 2.3 2.5 2.3H10M5 3.5a1.5 1.5 0 1 1-3 0 1.5 1.5 0 0 1 3 0Zm8 0a1.5 1.5 0 1 1-3 0 1.5 1.5 0 0 1 3 0Zm0 9a1.5 1.5 0 1 1-3 0 1.5 1.5 0 0 1 3 0Z" fill="none" stroke="currentColor" strokeWidth="1.2" />
        </svg>
    );
}
