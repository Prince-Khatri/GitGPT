import { Navigate, Route, Routes } from 'react-router-dom';
import Landing from './pages/Landing.jsx';
import Home from './pages/Home.jsx';
import Chat from './pages/Chat.jsx';
import Settings from './pages/Settings.jsx';
import Guide from './pages/Guide.jsx';
import { ToastHost } from './notify.jsx';

export default function App() {
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
