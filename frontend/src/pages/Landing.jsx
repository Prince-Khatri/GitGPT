import { Link, useSearchParams } from 'react-router-dom';

const FILES = [
    { name: '.github', folder: true },
    { name: 'packages', folder: true },
    { name: 'apps', folder: true },
    { name: 'docs', folder: true },
    { name: 'examples', folder: true },
    { name: 'scripts', folder: true },
    { name: 'README.md', folder: false },
    { name: 'package.json', folder: false },
    { name: 'tsconfig.json', folder: false }
];

export default function Landing() {
    const [params] = useSearchParams();
    const error = params.get('error');

    return (
        <div className="lp">
            <header className="lp-nav">
                <a className="lp-brand" href="#top">
                    <span className="lp-dot" />
                    GITGPT
                </a>
                <nav className="lp-links">
                    <Link to="/guide">How it works</Link>
                </nav>
                <a className="lp-signin" href="/oauth2/authorization/github">Sign in with GitHub</a>
            </header>

            <main id="top" className="lp-hero">
                <section className="lp-copy">
                    <p className="lp-badge">
                        <span className="lp-dot" />
                        Powered by your real code
                    </p>
                    <h1>
                        Chat with any GitHub repo,{' '}
                        <em>really understand it.</em>
                    </h1>
                    <p className="lp-lede">
                        GitGPT lets you explore, ask, and learn from a GitHub repository.
                        It reads the actual files in an indexed snapshot — not just docs —
                        so you get accurate, grounded answers.
                    </p>
                    {error && <p className="lp-error">{error}</p>}
                    <div className="lp-actions">
                        <a className="lp-cta" href="/oauth2/authorization/github">
                            <GitHubIcon />
                            Continue with GitHub
                            <span aria-hidden="true">→</span>
                        </a>
                        <Link className="lp-signin" to="/guide">See how it works</Link>
                    </div>
                    <p className="lp-fine">Free for developers. Add your own API key and use it as much as you want.</p>
                </section>

                <section className="lp-mock" aria-hidden="true">
                    <div className="lp-window">
                        <div className="lp-window-bar">
                            <span /><span /><span />
                        </div>
                        <div className="lp-window-body">
                            <aside className="lp-tree">
                                <p>Repository</p>
                                <div className="lp-repo-head">
                                    <span className="lp-repo-dot" />
                                    <div>
                                        <strong>vercel/next.js</strong>
                                        <small>The React Framework · Public</small>
                                    </div>
                                </div>
                                <ul>
                                    {FILES.map((file) => (
                                        <li key={file.name}>
                                            {file.folder ? <FolderIcon /> : <FileIcon />}
                                            {file.name}
                                        </li>
                                    ))}
                                </ul>
                            </aside>
                            <div className="lp-chat">
                                <p className="lp-q">How does image optimization work in this repo?</p>
                                <div className="lp-a">
                                    <span className="lp-bot" />
                                    <div>
                                        <p>
                                            In Next.js, image optimization is handled by the
                                            <code> next/image </code>
                                            component. It uses an on-demand image optimization
                                            service implemented in
                                            <code> packages/next/src/server/image-optimizer.ts</code>.
                                        </p>
                                        <p>Key points:</p>
                                        <ul>
                                            <li>Images are optimized on-demand when requested.</li>
                                            <li>Supports formats like WebP and AVIF.</li>
                                            <li>Handles resizing, and content-disposition headers.</li>
                                            <li>Configured via <code>next.config.js</code> under the <code>images</code> field.</li>
                                        </ul>
                                    </div>
                                </div>
                                <div className="lp-ask">
                                    <span>Ask a question about this repository…</span>
                                    <button type="button">→</button>
                                </div>
                            </div>
                        </div>
                    </div>
                </section>
            </main>

            <footer className="lp-foot">
                Built for developers who want to understand a codebase with their own keys.
            </footer>
        </div>
    );
}

function GitHubIcon() {
    return (
        <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">
            <path
                fill="currentColor"
                d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82A7.6 7.6 0 0 1 8 4.77c.68.003 1.36.092 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8"
            />
        </svg>
    );
}

function FolderIcon() {
    return (
        <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
            <path fill="#8b949e" d="M1.75 2A1.75 1.75 0 0 0 0 3.75v8.5C0 13.22.78 14 1.75 14h12.5A1.75 1.75 0 0 0 16 12.25v-6.5A1.75 1.75 0 0 0 14.25 4H7.5l-.69-1.03A1.75 1.75 0 0 0 5.36 2H1.75Z" />
        </svg>
    );
}

function FileIcon() {
    return (
        <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
            <path fill="#8b949e" d="M2 1.75C2 .784 2.784 0 3.75 0h6.586c.464 0 .909.184 1.237.513l2.914 2.914c.329.328.513.773.513 1.237v9.586A1.75 1.75 0 0 1 13.25 16h-9.5A1.75 1.75 0 0 1 2 14.25Zm1.75-.25a.25.25 0 0 0-.25.25v12.5c0 .138.112.25.25.25h9.5a.25.25 0 0 0 .25-.25V6h-2.75A1.75 1.75 0 0 1 9 4.25V1.5Zm6.75.062V4.25c0 .138.112.25.25.25h2.688Z" />
        </svg>
    );
}
