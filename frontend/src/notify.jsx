import { useEffect, useState } from 'react';

const recent = new Map();
const TOAST_EVENT = 'gitgpt-toast';

export function notify(message, type = 'info') {
    const text = String(message || '').trim();
    if (!text) {
        return;
    }
    const key = type + ':' + text;
    const now = Date.now();
    if ((recent.get(key) || 0) > now - 4000) {
        return;
    }
    recent.set(key, now);
    const toast = { id: now + Math.random(), message: text, type };
    window.dispatchEvent(new CustomEvent(TOAST_EVENT, { detail: toast }));
}

export function notifyRateLimit(message) {
    notify(message || 'Too many requests. Wait a few minutes and try again.', 'rate');
}

export function isRateLimit(error) {
    if (!error) {
        return false;
    }
    if (error.status === 429 || error.title === 'Rate limit') {
        return true;
    }
    const text = String(error.message || error.errorMessage || error).toLowerCase();
    return text.includes('429')
        || text.includes('rate limit')
        || text.includes('too many')
        || text.includes('resource_exhausted')
        || text.includes('quota');
}

export function ToastHost() {
    const [toasts, setToasts] = useState([]);

    useEffect(() => {
        function onToast(event) {
            const toast = event.detail;
            if (!toast) {
                return;
            }
            setToasts((current) => {
                const withoutOldRate = toast.type === 'rate'
                    ? current.filter((item) => item.type !== 'rate')
                    : current;
                return [...withoutOldRate.slice(-3), toast];
            });
            if (toast.type !== 'rate') {
                window.setTimeout(() => {
                    setToasts((current) => current.filter((item) => item.id !== toast.id));
                }, 5200);
            }
        }
        window.addEventListener(TOAST_EVENT, onToast);
        return () => window.removeEventListener(TOAST_EVENT, onToast);
    }, []);

    const rate = [...toasts].reverse().find((toast) => toast.type === 'rate');
    const stack = toasts.filter((toast) => toast.type !== 'rate');

    function dismiss(id) {
        setToasts((current) => current.filter((item) => item.id !== id));
    }

    if (!rate && stack.length === 0) {
        return null;
    }

    return (
        <>
            {rate && (
                <div className="rl-back" role="alertdialog" aria-modal="true" aria-labelledby="rl-title">
                    <div className="rl-card">
                        <div className="rl-icon" aria-hidden="true">!</div>
                        <h2 id="rl-title">Rate limit</h2>
                        <p>{rate.message}</p>
                        <button type="button" onClick={() => dismiss(rate.id)}>OK</button>
                    </div>
                </div>
            )}
            {stack.length > 0 && (
                <div className="toast-stack" role="status" aria-live="polite">
                    {stack.map((toast) => (
                        <aside key={toast.id} className={'toast toast-' + toast.type}>
                            <strong>{toast.type === 'error' ? 'Error' : toast.type === 'ok' ? 'Saved' : 'Notice'}</strong>
                            <p>{toast.message}</p>
                            <button type="button" onClick={() => dismiss(toast.id)}>×</button>
                        </aside>
                    ))}
                </div>
            )}
        </>
    );
}
