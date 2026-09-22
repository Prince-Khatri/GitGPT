const PROD_API = 'https://giptgpt-ai-backend.onrender.com';

function trimSlash(value) {
    return String(value || '').trim().replace(/\/+$/, '');
}

function hostedOnNetlify() {
    return typeof window !== 'undefined' && /\.netlify\.app$/i.test(window.location.hostname);
}

/** Public API origin. Empty means same origin (Vite proxy or nginx). */
export function apiBase() {
    const runtime = typeof window !== 'undefined' ? trimSlash(window.GITGPT_API_BASE_URL) : '';
    const built = trimSlash(import.meta.env.VITE_API_BASE_URL);
    if (runtime || built) {
        return runtime || built;
    }
    return hostedOnNetlify() ? PROD_API : '';
}

export function apiUrl(path) {
    const suffix = path.startsWith('/') ? path : '/' + path;
    const base = apiBase();
    return base ? base + suffix : suffix;
}

export function githubLoginUrl() {
    return apiUrl('/oauth2/authorization/github');
}
