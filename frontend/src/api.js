import { apiUrl } from './config.js';

function apiFetch(path, options = {}) {
    return fetch(apiUrl(path), { credentials: 'include', ...options });
}

async function csrfHeaders() {
    const headers = {};
    const cookie = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/);
    if (cookie) {
        headers['X-XSRF-TOKEN'] = decodeURIComponent(cookie[1]);
    }
    const response = await apiFetch('/api/csrf');
    if (!response.ok) {
        return headers;
    }
    const data = await response.json();
    if (data.headerName && data.token) {
        headers[data.headerName] = data.token;
    }
    return headers;
}

export class ApiError extends Error {
    constructor(message, status, title) {
        super(message);
        this.name = 'ApiError';
        this.status = status;
        this.title = title;
    }
}

async function readError(response, fallback) {
    try {
        const data = await response.json();
        return new ApiError(data.message || data.title || fallback, response.status, data.title);
    } catch {
        return new ApiError(fallback, response.status);
    }
}

async function throwIfNotOk(response, fallback) {
    if (response.ok) {
        return;
    }
    throw await readError(response, fallback);
}

export async function getGeminiSettings() {
    const response = await apiFetch('/api/me/gemini');
    await throwIfNotOk(response, 'Could not load Gemini settings.');
    return response.json();
}

export async function saveGeminiSettings(payload) {
    const headers = await csrfHeaders();
    const response = await apiFetch('/api/me/gemini', {
        method: 'PUT',
        headers: {
            ...headers,
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(payload)
    });
    await throwIfNotOk(response, 'Could not save Gemini settings.');
    return response.json();
}

export async function clearGeminiKey() {
    const headers = await csrfHeaders();
    const response = await apiFetch('/api/me/gemini', {
        method: 'DELETE',
        headers
    });
    await throwIfNotOk(response, 'Could not remove the Gemini API key.');
    return response.json();
}

export async function getMe() {
    const response = await apiFetch('/api/me');
    if (response.status === 401) {
        return null;
    }
    if (!response.ok) {
        return null;
    }
    return response.json();
}

export async function logout() {
    const headers = await csrfHeaders();
    await apiFetch('/logout', { method: 'POST', headers });
    window.location.href = '/';
}

export async function getRepo(repoId) {
    const response = await apiFetch('/api/repos/' + repoId);
    if (!response.ok) {
        throw new Error('Could not load this repository.');
    }
    return response.json();
}

export async function getCachedRepos() {
    const response = await apiFetch('/api/repos');
    if (!response.ok) {
        throw new Error('Could not load cached repositories.');
    }
    return response.json();
}

export async function getGithubRepos(page) {
    const response = await apiFetch('/api/repos/github?page=' + page);
    await throwIfNotOk(response, 'Could not load GitHub repositories.');
    return response.json();
}

export async function getChats(repoId) {
    const path = repoId ? '/api/repos/' + repoId + '/chats' : '/api/chats';
    const response = await apiFetch(path);
    if (!response.ok) {
        throw new Error('Could not load chat history.');
    }
    return response.json();
}

export async function startChat(repoId) {
    const headers = await csrfHeaders();
    const response = await apiFetch('/api/repos/' + repoId + '/chats', {
        method: 'POST',
        headers
    });
    if (!response.ok) {
        throw new Error('Could not start a new conversation.');
    }
    return response.json();
}

export async function getThread(repoId, sessionId) {
    const query = sessionId ? '?sessionId=' + sessionId : '';
    const response = await apiFetch('/api/repos/' + repoId + '/chat' + query);
    if (!response.ok) {
        throw new Error('Could not load this conversation.');
    }
    return response.json();
}

export async function startIndex(repoId) {
    const headers = await csrfHeaders();
    const response = await apiFetch('/api/repos/' + repoId + '/index', {
        method: 'POST',
        headers
    });
    await throwIfNotOk(response, 'Could not start indexing.');
    return response.json();
}

export async function cancelIndex(repoId) {
    const headers = await csrfHeaders();
    const response = await apiFetch('/api/repos/' + repoId + '/index/cancel', {
        method: 'POST',
        headers
    });
    await throwIfNotOk(response, 'Could not cancel indexing.');
    return response.json();
}

export async function getIndexStatus(repoId) {
    const response = await apiFetch('/api/repos/' + repoId + '/index');
    await throwIfNotOk(response, 'Could not read index status.');
    return response.json();
}

export async function streamAsk(repoId, sessionId, question, onEvent) {
    const headers = await csrfHeaders();
    const controller = new AbortController();
    const timer = window.setTimeout(() => controller.abort(), 95_000);
    let response;
    try {
        response = await apiFetch('/api/repos/' + repoId + '/ask/stream', {
            method: 'POST',
            signal: controller.signal,
            headers: {
                ...headers,
                Accept: 'text/event-stream',
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ question, sessionId: sessionId || undefined })
        });
    } catch (ex) {
        window.clearTimeout(timer);
        if (ex && ex.name === 'AbortError') {
            throw new ApiError('The model took too long to answer. Try again or pick a faster model in Settings.', 408);
        }
        throw ex;
    }
    if (!response.ok || !response.body) {
        window.clearTimeout(timer);
        throw await readError(response, 'Ask failed.');
    }
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let eventName = 'message';
    let failure = null;
    try {
        while (true) {
            const { value, done } = await reader.read();
            buffer += decoder.decode(value || new Uint8Array(), { stream: !done });
            const parts = buffer.split(/\r?\n/);
            buffer = done ? '' : parts.pop();
            const lines = done ? parts.concat(buffer ? [buffer] : []) : parts;
            for (const raw of lines) {
                const line = raw.trimEnd();
                if (line.startsWith('event:')) {
                    eventName = line.slice(6).trim();
                } else if (line.startsWith('data:')) {
                    let data = line.slice(5).trim();
                    try {
                        data = JSON.parse(data);
                    } catch {
                        // keep raw
                    }
                    if (eventName === 'fail' || eventName === 'error') {
                        const message = typeof data === 'string' ? data : (data && data.message) || 'Ask failed.';
                        const rate = isRateLimitStatus(message);
                        failure = new ApiError(message, rate ? 429 : 400, rate ? 'Rate limit' : 'Ask failed');
                    }
                    onEvent(eventName, data);
                    eventName = 'message';
                }
            }
            if (done) {
                break;
            }
        }
    } finally {
        window.clearTimeout(timer);
    }
    if (failure) {
        throw failure;
    }
}

function isRateLimitStatus(message) {
    const text = String(message || '').toLowerCase();
    return text.includes('429')
        || text.includes('rate limit')
        || text.includes('too many')
        || text.includes('resource_exhausted')
        || text.includes('quota');
}
