import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const api = String(process.env.VITE_API_BASE_URL || '').trim().replace(/\/+$/, '');
if (!api) {
    console.log('VITE_API_BASE_URL is empty; leaving Netlify proxy redirects unset.');
    process.exit(0);
}

const rules = [
    `/oauth2/*  ${api}/oauth2/:splat  302!`,
    `/login/*   ${api}/login/:splat  302!`,
    `/logout    ${api}/logout  302!`,
    `/api/*     ${api}/api/:splat  200!`,
    ''
].join('\n');

const dist = join(dirname(fileURLToPath(import.meta.url)), '..', 'dist');
mkdirSync(dist, { recursive: true });
writeFileSync(join(dist, '_redirects'), rules);
console.log(`Wrote Netlify proxy redirects for ${api}`);
