function trimSlash(value) {
    return String(value || '').trim().replace(/\/+$/, '');
}

/** Public API origin. Empty means same origin (Vite proxy, nginx, or Netlify redirects). */
export function apiBase() {
    const runtime = typeof window !== 'undefined' ? trimSlash(window.GITGPT_API_BASE_URL) : '';
    return runtime;
}

export function apiUrl(path) {
    const suffix = path.startsWith('/') ? path : '/' + path;
    const base = apiBase();
    return base ? base + suffix : suffix;
}

export function githubLoginUrl() {
    return apiUrl('/oauth2/authorization/github');
}
