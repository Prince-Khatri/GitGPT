import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { clearGeminiKey, getCachedRepos, getGeminiSettings, getMe, saveGeminiSettings } from '../api.js';
import { isRateLimit, notify, notifyRateLimit } from '../notify.jsx';
import UserMenu from './UserMenu.jsx';

const TABS = [
    { id: 'profile', label: 'Profile' },
    { id: 'keys', label: 'API keys' },
    { id: 'models', label: 'Models' }
];

export default function Settings() {
    const [params, setParams] = useSearchParams();
    const tab = TABS.some((item) => item.id === params.get('tab')) ? params.get('tab') : 'profile';
    const [user, setUser] = useState(null);
    const [settings, setSettings] = useState(null);
    const [repos, setRepos] = useState([]);
    const [apiKey, setApiKey] = useState('');
    const [chatModel, setChatModel] = useState('');
    const [embeddingModel, setEmbeddingModel] = useState('');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');

    useEffect(() => {
        getMe().then((me) => {
            if (!me) {
                window.location.href = '/';
                return;
            }
            setUser(me);
            getCachedRepos().then(setRepos).catch(() => {});
            return getGeminiSettings().then((data) => {
                setSettings(data);
                setChatModel(data.chatModel);
                setEmbeddingModel(data.embeddingModel);
            }).catch((ex) => {
                setError(ex.message);
                setSettings({
                    hasUserKey: Boolean(me.hasGeminiKey),
                    hasServerFallback: Boolean(me.hasServerGeminiKey),
                    keyHint: null,
                    chatModel: me.chatModel || 'gemini-3.5-flash-lite',
                    embeddingModel: me.embeddingModel || 'gemini-embedding-001',
                    embeddingDimensions: 1536,
                    chatModels: [],
                    embeddingModels: []
                });
                setChatModel(me.chatModel || 'gemini-3.5-flash-lite');
                setEmbeddingModel(me.embeddingModel || 'gemini-embedding-001');
            });
        }).catch(() => {
            window.location.href = '/';
        });
    }, []);

    function openTab(next) {
        setParams({ tab: next }, { replace: true });
        setError('');
    }

    async function onSave(event) {
        event.preventDefault();
        setSaving(true);
        setError('');
        try {
            const next = await saveGeminiSettings({
                apiKey: apiKey.trim() || undefined,
                chatModel,
                embeddingModel
            });
            setSettings(next);
            setApiKey('');
            setChatModel(next.chatModel);
            setEmbeddingModel(next.embeddingModel);
            notify(next.hasUserKey ? 'Gemini settings saved.' : 'Models saved.', 'ok');
        } catch (ex) {
            if (isRateLimit(ex)) {
                notifyRateLimit(ex.message);
            } else {
                setError(ex.message);
                notify(ex.message, 'error');
            }
        } finally {
            setSaving(false);
        }
    }

    async function onRemoveKey() {
        setSaving(true);
        setError('');
        try {
            const next = await clearGeminiKey();
            setSettings(next);
            setApiKey('');
            notify('Gemini API key removed.', 'ok');
        } catch (ex) {
            setError(ex.message);
            notify(ex.message, 'error');
        } finally {
            setSaving(false);
        }
    }

    if (!user || !settings) {
        return <div className="hm hm-boot">Loading…</div>;
    }

    const indexedCount = repos.filter((repo) => repo.indexStatus === 'READY').length;
    const canIndex = settings.hasUserKey || settings.hasServerFallback;

    return (
        <div className="hm">
            <header className="hm-top">
                <Link className="hm-brand" to="/home">
                    <span className="lp-dot" />
                    GITGPT
                </Link>
                <div />
                <UserMenu user={user} />
            </header>
            <div className="hm-body">
                <aside className="hm-side">
                    <Link className="hm-nav" to="/home"><HomeIcon /> Home</Link>
                    <Link className="hm-nav active" to="/settings"><GearIcon /> Settings</Link>
                    <div className="st-sub">
                        {TABS.map((item) => (
                            <button
                                key={item.id}
                                type="button"
                                className={'st-sub-link' + (tab === item.id ? ' on' : '')}
                                onClick={() => openTab(item.id)}
                            >
                                {item.label}
                            </button>
                        ))}
                    </div>
                    <p className="hm-side-label">Free for developers</p>
                    <p className="hint" style={{ padding: '0 12px' }}>
                        Bring your own API key. No billing in GitGPT.
                    </p>
                </aside>

                <section className="st-main">
                    <p className="eyebrow">Account</p>
                    <h1>Account settings</h1>
                    <p className="lede">Manage your profile, API keys, and models. GitGPT is free — you pay only your model provider.</p>

                    <nav className="st-tabs">
                        {TABS.map((item) => (
                            <button
                                key={item.id}
                                type="button"
                                className={tab === item.id ? 'on' : ''}
                                onClick={() => openTab(item.id)}
                            >
                                {item.label}
                            </button>
                        ))}
                    </nav>

                    {tab === 'profile' && (
                        <div className="st-grid">
                            <article className="st-card">
                                <h2>Profile information</h2>
                                <p>These details come from your GitHub account.</p>
                                <div className="st-profile">
                                    <span className="st-avatar">
                                        {user.urlAvatar ? <img src={user.urlAvatar} alt="" /> : (user.githubUsername || 'U').slice(0, 1)}
                                    </span>
                                    <label className="st-field">
                                        <span>Name</span>
                                        <input value={user.githubUsername || ''} readOnly />
                                    </label>
                                    <label className="st-field">
                                        <span>Email</span>
                                        <input value={user.email || ''} readOnly />
                                    </label>
                                </div>
                            </article>

                            <aside className="st-card">
                                <h2>Your setup</h2>
                                <p>No plans or usage caps inside GitGPT.</p>
                                <ul className="st-facts">
                                    <li><span>Gemini key</span><b>{settings.hasUserKey ? 'Saved' : canIndex ? 'Server fallback' : 'Missing'}</b></li>
                                    <li><span>Answer model</span><b>{settings.chatModel}</b></li>
                                    <li><span>Embedding model</span><b>{settings.embeddingModel}</b></li>
                                    <li><span>Indexed repos</span><b>{indexedCount}</b></li>
                                </ul>
                                <Link className="st-save" to="/guide">See how GitGPT works</Link>
                            </aside>

                            <article className="st-card st-wide">
                                <h2>Connected accounts</h2>
                                <p>Sign in stays on GitHub. Tokens are encrypted at rest.</p>
                                <div className="st-connect">
                                    <div>
                                        <strong>GitHub</strong>
                                        <span>Connected as {user.githubUsername}</span>
                                    </div>
                                    <em>Connected</em>
                                </div>
                            </article>
                        </div>
                    )}

                    {tab === 'keys' && (
                        <form className="st-card" onSubmit={onSave}>
                            <h2>API keys</h2>
                            <p>GitGPT never sells access. Add your own provider key, then pick models.</p>
                            <label className="st-field">
                                <span>Gemini API key</span>
                                <input
                                    type="password"
                                    autoComplete="off"
                                    value={apiKey}
                                    onChange={(event) => setApiKey(event.target.value)}
                                    placeholder={settings.hasUserKey
                                        ? 'Key saved. Paste a new key to replace it.'
                                        : 'Paste your Google AI Studio key'}
                                />
                                <small>
                                    Get a key from{' '}
                                    <a href="https://aistudio.google.com/apikey" target="_blank" rel="noreferrer">Google AI Studio</a>.
                                    {settings.hasUserKey ? ' A key is saved. It is never shown again.' : ''}
                                </small>
                            </label>
                            <div className="st-soon">
                                <p><strong>OpenAI</strong> Coming next — bring your own key and choose a model.</p>
                                <p><strong>Anthropic</strong> Coming next — same bring-your-own-key flow.</p>
                            </div>
                            {error && <p className="st-error">{error}</p>}
                            {!canIndex && <p className="st-warn">Add a Gemini key before indexing or asking.</p>}
                            <div className="st-actions">
                                <button className="st-save" type="submit" disabled={saving}>{saving ? 'Saving…' : 'Save key'}</button>
                                {settings.hasUserKey && (
                                    <button className="st-remove" type="button" disabled={saving} onClick={onRemoveKey}>Remove key</button>
                                )}
                            </div>
                        </form>
                    )}

                    {tab === 'models' && (
                        <form className="st-card" onSubmit={onSave}>
                            <h2>Models</h2>
                            <p>
                                Answer models come from your Gemini API key, not a fixed GitGPT list.
                                Embedding stays on a 1536-dimension model so the vector store stays compatible.
                            </p>
                            <label className="st-field">
                                <span>Answer model</span>
                                <select value={chatModel} onChange={(event) => setChatModel(event.target.value)}>
                                    {(settings.chatModels.length ? settings.chatModels : [{ id: chatModel, label: chatModel }]).map((model) => (
                                        <option key={model.id} value={model.id}>{model.label}</option>
                                    ))}
                                </select>
                                <small>{descriptionOf(settings.chatModels, chatModel)}</small>
                            </label>
                            <label className="st-field">
                                <span>Embedding model</span>
                                <select value={embeddingModel} onChange={(event) => setEmbeddingModel(event.target.value)}>
                                    {(settings.embeddingModels.length ? settings.embeddingModels : [{ id: embeddingModel, label: embeddingModel }]).map((model) => (
                                        <option key={model.id} value={model.id}>{model.label}</option>
                                    ))}
                                </select>
                                <small>
                                    {descriptionOf(settings.embeddingModels, embeddingModel)}
                                    {' '}Embeddings stay at {settings.embeddingDimensions || 1536} dimensions. Re-index after changing this.
                                </small>
                            </label>
                            {error && <p className="st-error">{error}</p>}
                            <div className="st-actions">
                                <button className="st-save" type="submit" disabled={saving}>{saving ? 'Saving…' : 'Save models'}</button>
                            </div>
                        </form>
                    )}
                </section>
            </div>
        </div>
    );
}

function descriptionOf(models, id) {
    return (models || []).find((model) => model.id === id)?.description || '';
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
