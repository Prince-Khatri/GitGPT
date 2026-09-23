import { useEffect, useState } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { completeLoginIfNeeded } from './api.js';
import Landing from './pages/Landing.jsx';
import Home from './pages/Home.jsx';
import Chat from './pages/Chat.jsx';
import Settings from './pages/Settings.jsx';
import Guide from './pages/Guide.jsx';
import { ToastHost } from './notify.jsx';

export default function App() {
    const [ready, setReady] = useState(false);

    useEffect(() => {
        completeLoginIfNeeded()
            .catch((ex) => {
                const ticket = new URLSearchParams(window.location.search).get('ticket');
                if (ticket) {
                    window.location.replace('/?error=' + encodeURIComponent(ex.message || 'Sign-in did not complete.'));
                }
            })
            .finally(() => setReady(true));
    }, []);

    if (!ready) {
        return <div className="hm hm-boot">Signing you in…</div>;
    }

    return (
        <>
            <Routes>
                <Route path="/" element={<Landing />} />
                <Route path="/guide" element={<Guide />} />
                <Route path="/home" element={<Home />} />
                <Route path="/settings" element={<Settings />} />
                <Route path="/profile" element={<Navigate to="/settings" replace />} />
                <Route path="/repos/:repoId" element={<Chat />} />
                <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
            <ToastHost />
        </>
    );
}
