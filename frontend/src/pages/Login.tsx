/**
 * Login page.
 *
 * Username + password form. On submit:
 *  1. POST /api/auth/login
 *  2. On 200: store the token (the AuthContext does it), navigate to
 *     /change-password if the response says mustChangePassword=true,
 *     else /dashboard (or ?next= if it's a safe internal path).
 *  3. On 401: inline "Invalid credentials" — no field-level enumeration
 *     (defense against username enumeration).
 */
import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';

interface LocationState {
  from?: string;
}

function isInternalPath(p: string): boolean {
  return p.startsWith('/') && !p.startsWith('//');
}

export function Login() {
  const { user, login, isLoading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // If the user is already logged in, bounce away from /login.
  if (!isLoading && user) {
    return <Navigate to="/dashboard" replace />;
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!username.trim() || !password) {
      setError('Please enter a username and password');
      return;
    }
    setSubmitting(true);
    try {
      const u = await login(username.trim(), password);
      // Determine where to go after login.
      const state = location.state as LocationState | null;
      const nextParam = searchParams.get('next');
      const fallback = state?.from && isInternalPath(state.from) ? state.from : null;
      const target = u.mustChangePassword
        ? '/change-password'
        : nextParam && isInternalPath(nextParam)
          ? nextParam
          : (fallback ?? '/dashboard');
      navigate(target, { replace: true });
    } catch (err) {
      // Both bad username and bad password surface the same message
      // (no enumeration). Other errors (network) get the message from
      // the api/client's envelope unwrap.
      setError(err instanceof Error ? err.message : 'Invalid credentials');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="login-screen">
      <form className="login-card" onSubmit={onSubmit} noValidate>
        <h1>BPL Order Engine Admin</h1>
        <p className="login-card__subtitle">
          Sign in to monitor and control the engines.
        </p>

        <div className="login-card__divider">Sign in</div>

        <div className="login-card__field">
          <label htmlFor="login-username">Username</label>
          <input
            id="login-username"
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            spellCheck={false}
            disabled={submitting}
            required
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
            disabled={submitting}
            required
          />
        </div>

        {error && (
          <div className="login-card__error" role="alert">
            {error}
          </div>
        )}

        <button
          type="submit"
          className="btn btn--primary btn--block"
          disabled={submitting}
          style={{ marginTop: 8 }}
        >
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>

        <p className="login-card__hint">
          Demo creds: <code>sysadmin / sysadmin123</code> · <code>admin / admin123</code> ·{' '}
          <code>user1 / user123</code>
          <br />
          (Mock mode: any seed user accepts password{' '}
          <code>&lt;username&gt;123</code>.)
        </p>
      </form>
    </div>
  );
}
