import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const upstream = String(process.env.BACKEND_UPSTREAM || '').trim().replace(/\/+$/, '');
if (!upstream) {
    throw new Error('BACKEND_UPSTREAM is required to build the Netlify proxy redirects.');
}

const url = new URL(upstream);
if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
    throw new Error('BACKEND_UPSTREAM must be an HTTP(S) origin without credentials, query, or fragment.');
}

const rules = [
    `/oauth2/*  ${upstream}/oauth2/:splat  200!`,
    `/login/*   ${upstream}/login/:splat  200!`,
    `/logout    ${upstream}/logout  200!`,
    `/api/*     ${upstream}/api/:splat  200!`,
    ''
].join('\n');

const dist = join(dirname(fileURLToPath(import.meta.url)), '..', 'dist');
mkdirSync(dist, { recursive: true });
writeFileSync(join(dist, '_redirects'), rules);
console.log(`Wrote Netlify proxy redirects for ${url.origin}`);
