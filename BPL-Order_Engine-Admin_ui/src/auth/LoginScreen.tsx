import { useState, type FormEvent } from 'react';
import { useAuth } from './useAuth';
import { DEMO_USERS } from './types';

/**
 * Login screen. Two paths in:
 *   1. Quick-login buttons ("Login as Admin" / "Login as Viewer") that
 *      pre-fill the credentials from DEMO_USERS and submit immediately.
 *   2. Manual username/password form for the keyboard-inclined.
 *
 * Either way the AuthContext updates, the AppShell re-renders, and the
 * dashboard takes over. There is no separate "submit" step for the
 * quick-login buttons because the credentials are known-good against
 * the in-memory backend; we just stash them.
 */
export function LoginScreen() {
  const { signIn, signInAs } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    const ok = signIn(username, password);
    if (!ok) {
      setError('Invalid username or password');
    }
  }

  return (
    <div className="login-screen">
      <form className="login-card" onSubmit={handleSubmit} noValidate>
        <h1>BPL Order Engine Admin</h1>
        <p className="login-card__subtitle">Sign in to monitor and control the engine.</p>

        <div className="login-card__divider">Quick sign-in</div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
          <button
            type="button"
            className="btn btn--primary"
            onClick={() => signInAs('ADMIN')}
            aria-label="Sign in as Admin (admin / admin123)"
          >
            Login as Admin
          </button>
          <button
            type="button"
            className="btn"
            onClick={() => signInAs('VIEWER')}
            aria-label="Sign in as Viewer (viewer / viewer123)"
          >
            Login as Viewer
          </button>
        </div>

        <div className="login-card__divider">or sign in manually</div>

        <div className="login-card__field">
          <label htmlFor="login-username">Username</label>
          <input
            id="login-username"
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            spellCheck={false}
          />
        </div>

        <div className="login-card__field">
          <label htmlFor="login-password">Password</label>
          <input
            id="login-password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
          />
        </div>

        {error && (
          <div className="login-card__error" role="alert">
            {error}
          </div>
        )}

        <button type="submit" className="btn btn--primary btn--block" style={{ marginTop: 8 }}>
          Sign in
        </button>

        <p className="login-card__hint">
          Demo creds: <code>admin / admin123</code> &middot; <code>viewer / viewer123</code>
          <br />
          (Hint: <code>admin</code> and <code>viewer</code> match{' '}
          <code>{DEMO_USERS.ADMIN.username}</code> / <code>{DEMO_USERS.VIEWER.username}</code>)
        </p>
      </form>
    </div>
  );
}
