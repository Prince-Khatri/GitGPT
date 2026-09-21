import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { logout } from '../api.js';

export default function UserMenu({ user }) {
    const [open, setOpen] = useState(false);
    const root = useRef(null);
    const initial = (user?.githubUsername || 'U').slice(0, 1).toUpperCase();

    useEffect(() => {
        function onClick(event) {
            if (root.current && !root.current.contains(event.target)) {
                setOpen(false);
            }
        }
        document.addEventListener('mousedown', onClick);
        return () => document.removeEventListener('mousedown', onClick);
    }, []);

    return (
        <div className="um" ref={root}>
            <button className="hm-avatar" type="button" onClick={() => setOpen((value) => !value)} title="Account">
                {user?.urlAvatar ? <img src={user.urlAvatar} alt="" /> : initial}
            </button>
            {open && (
                <div className="um-menu">
                    <p>{user?.githubUsername || 'Account'}</p>
                    <Link to="/settings" onClick={() => setOpen(false)}>Profile &amp; API keys</Link>
                    <button type="button" onClick={() => logout()}>Sign out</button>
                </div>
            )}
        </div>
    );
}
