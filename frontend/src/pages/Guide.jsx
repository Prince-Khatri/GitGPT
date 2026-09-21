import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

const STEPS = [
    {
        id: 'signin',
        title: 'Sign in with GitHub',
        copy: 'OAuth only. GitGPT lists your repos one page at a time and never embeds on login.'
    },
    {
        id: 'key',
        title: 'Add your Gemini key',
        copy: 'Open Profile → API keys. Paste a Google AI Studio key. It is encrypted at rest. You pick the models.'
    },
    {
        id: 'index',
        title: 'Index one repository',
        copy: 'Choose a repo on Home. GitGPT snapshots the default branch, chunks the code, and stores 1536-d embeddings.'
    },
    {
        id: 'ask',
        title: 'Ask from the snapshot',
        copy: 'Questions retrieve from your indexed files every turn. Answers stream, and citations open the exact GitHub lines.'
    }
];

export default function Guide() {
    const [step, setStep] = useState(0);

    useEffect(() => {
        const timer = window.setInterval(() => {
            setStep((current) => (current + 1) % STEPS.length);
        }, 4200);
        return () => window.clearInterval(timer);
    }, []);

    const active = STEPS[step];

    return (
        <div className="gd">
            <header className="lp-nav">
                <Link className="lp-brand" to="/">
                    <span className="lp-dot" />
                    GITGPT
                </Link>
                <nav className="lp-links">
                    <Link to="/">Home</Link>
                    <Link to="/guide">How it works</Link>
                </nav>
                <a className="lp-signin" href="/oauth2/authorization/github">Sign in with GitHub</a>
            </header>

            <main className="gd-hero">
                <p className="lp-badge"><span className="lp-dot" /> A short guide</p>
                <h1>How developers use GitGPT</h1>
                <p className="lp-lede">
                    No billing page. No shared quota. Sign in, add your own API key, index one repo, and ask.
                    OpenAI and Anthropic keys come next — same bring-your-own-key idea.
                </p>
            </main>

            <section className="gd-stage">
                <ol className="gd-steps">
                    {STEPS.map((item, index) => (
                        <li key={item.id}>
                            <button
                                type="button"
                                className={index === step ? 'on' : index < step ? 'done' : ''}
                                onClick={() => setStep(index)}
                            >
                                <i>{index + 1}</i>
                                <strong>{item.title}</strong>
                                <span>{item.copy}</span>
                            </button>
                        </li>
                    ))}
                </ol>

                <div className={'gd-demo gd-' + active.id} aria-hidden="true">
                    <div className="gd-window">
                        <div className="lp-window-bar"><span /><span /><span /></div>
                        {active.id === 'signin' && (
                            <div className="gd-panel gd-fade">
                                <p>Continue with GitHub</p>
                                <div className="gd-btn">Sign in with GitHub</div>
                                <small>read:user · user:email · repo</small>
                            </div>
                        )}
                        {active.id === 'key' && (
                            <div className="gd-panel gd-fade">
                                <label>Gemini API key</label>
                                <div className="gd-type">AIza••••••••••••••••</div>
                                <div className="gd-pills">
                                    <em>Answer: Flash-Lite</em>
                                    <em>Embed: gemini-embedding-001</em>
                                </div>
                            </div>
                        )}
                        {active.id === 'index' && (
                            <div className="gd-panel gd-fade">
                                <strong>acme/payments</strong>
                                <div className="gd-bar"><i /></div>
                                <ul>
                                    <li className="on">Cloning repository</li>
                                    <li className="on">Processing files</li>
                                    <li>Creating embeddings</li>
                                </ul>
                            </div>
                        )}
                        {active.id === 'ask' && (
                            <div className="gd-panel gd-fade">
                                <p className="gd-q">Where is checkout validated?</p>
                                <p className="gd-a">In <code>CheckoutService.java</code> the total is checked before the charge.</p>
                                <a className="gd-cite">src/checkout/CheckoutService.java L42-L68 →</a>
                            </div>
                        )}
                    </div>
                </div>
            </section>

            <section className="gd-features">
                <article>
                    <h3>Grounded in the snapshot</h3>
                    <p>Answers come from retrieved files at a commit SHA, not a live crawl or invented APIs.</p>
                </article>
                <article>
                    <h3>Your key, your models</h3>
                    <p>Pick Gemini chat and embedding models now. More providers later. Rate limits pop up instead of failing silently.</p>
                </article>
                <article>
                    <h3>One repo at a time</h3>
                    <p>Listing never embeds. You choose when to index. Follow-ups stay on that snapshot.</p>
                </article>
            </section>

            <div className="gd-cta">
                <a className="lp-cta" href="/oauth2/authorization/github">Start with GitHub</a>
                <Link className="lp-signin" to="/">Back to home</Link>
            </div>
        </div>
    );
}
