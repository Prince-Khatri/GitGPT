const STEPS = [
    { id: 'CLONE', label: 'Cloning repository' },
    { id: 'ANALYZE', label: 'Analyzing file structure' },
    { id: 'PROCESS', label: 'Processing code files' },
    { id: 'EMBED', label: 'Creating embeddings' },
    { id: 'STORE', label: 'Saving to vector store' },
    { id: 'FINALIZE', label: 'Finalizing' }
];

export default function IndexingModal({ repoName, job, onCancel }) {
    if (!job) {
        return null;
    }
    const failed = String(job.status || '').toUpperCase() === 'FAILED';
    const ready = String(job.status || '').toUpperCase() === 'READY';
    const percent = ready ? 100 : Math.max(0, Math.min(100, job.progressPercent ?? (job.status === 'QUEUED' ? 8 : 35)));
    const current = ready ? 'FINALIZE' : (job.progressStep || 'CLONE');
    const currentIndex = STEPS.findIndex((step) => step.id === current);

    return (
        <div className="ix-back">
            <div className="ix-card" role="dialog" aria-labelledby="ix-title">
                <div className="ix-icon">
                    <svg viewBox="0 0 32 32" width="36" height="36" aria-hidden="true">
                        <ellipse cx="16" cy="8" rx="10" ry="4" fill="none" stroke="currentColor" strokeWidth="1.8" />
                        <path d="M6 8v16c0 2.2 4.5 4 10 4s10-1.8 10-4V8" fill="none" stroke="currentColor" strokeWidth="1.8" />
                        <path d="M6 16c0 2.2 4.5 4 10 4s10-1.8 10-4" fill="none" stroke="currentColor" strokeWidth="1.8" />
                    </svg>
                </div>
                <h2 id="ix-title">Indexing repository</h2>
                <strong>{repoName}</strong>
                <p>{failed
                    ? (job.errorMessage || 'Indexing failed.')
                    : 'Analyzing code, creating embeddings, and building the knowledge base…'}</p>
                <div className="ix-bar-wrap">
                    <div className="ix-bar"><i style={{ width: percent + '%' }} /></div>
                    <span>{percent}%</span>
                </div>
                <ul className="ix-steps">
                    {STEPS.map((step, index) => {
                        const done = ready || index < currentIndex || (index === currentIndex && (ready || percent >= 100));
                        const active = !failed && !done && index === currentIndex;
                        return (
                            <li key={step.id} className={done ? 'done' : active ? 'on' : ''}>
                                <span>{done ? '✓' : ''}</span>
                                {step.label}
                            </li>
                        );
                    })}
                </ul>
                {!ready && (
                    <button type="button" onClick={onCancel} disabled={!!job.cancelRequested && !failed}>
                        {failed ? 'Close' : job.cancelRequested ? 'Cancelling…' : 'Cancel'}
                    </button>
                )}
            </div>
        </div>
    );
}

export function isIndexing(status) {
    const value = String(status || '').toUpperCase();
    return value === 'QUEUED' || value === 'RUNNING';
}
