/**
 * ChangePassword page.
 *
 * Three-field form: current password, new password, confirm new
 * password. On submit:
 *  1. Client-side check: new and confirm must match (inline error if not).
 *  2. POST /api/auth/change-password with { currentPassword, newPassword }.
 *  3. On 200: the new token is already stored by AuthContext; navigate
 *     to /dashboard.
 *  4. On 401: "Current password is incorrect." (the api/client's
 *     envelope unwrap surfaces the server's message).
 *  5. On 422: the server's validation message is shown verbatim.
 *
 * This page is the only authenticated route the user can reach while
 * mustChangePassword=true. The route guard in App.tsx bounces any
 * attempt to visit /dashboard, /logs, or /admin back to this page.
 */
import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';

const MIN_NEW = 12;
const NEW_HINT = `At least ${MIN_NEW} characters, including a letter and a digit.`;

export function ChangePassword() {
  const { user, mustChangePassword, changePassword, isLoading } = useAuth();
  const navigate = useNavigate();

  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [matchError, setMatchError] = useState<string | null>(null);

  // No auth -> back to /login.
  if (!isLoading && !user) {
    return <Navigate to="/login" replace />;
  }
  // Already changed -> shouldn't be here.
  if (!isLoading && user && !mustChangePassword) {
    return <Navigate to="/dashboard" replace />;
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setMatchError(null);

    if (!current || !next || !confirm) {
      setError('Please complete all fields.');
      return;
    }
    if (next !== confirm) {
      setMatchError('New password and confirmation do not match.');
      return;
    }
    if (next.length < MIN_NEW || !/[A-Za-z]/.test(next) || !/\d/.test(next)) {
      setError(NEW_HINT);
      return;
    }
    setSubmitting(true);
    try {
      await changePassword(current, next);
      navigate('/dashboard', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to change password');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="login-screen">
      <form className="login-card" onSubmit={onSubmit} noValidate>
        <h1>Change your password</h1>
        <p className="login-card__subtitle">
          You must set a new password before continuing.
        </p>

        <div className="login-card__divider">New password</div>

        <div className="login-card__field">
          <label htmlFor="cp-current">Current password</label>
          <input
            id="cp-current"
            type="password"
            value={current}
            onChange={(e) => setCurrent(e.target.value)}
            autoComplete="current-password"
            disabled={submitting}
            required
          />
        </div>

        <div className="login-card__field">
          <label htmlFor="cp-next">New password</label>
          <input
            id="cp-next"
            type="password"
            value={next}
            onChange={(e) => setNext(e.target.value)}
            autoComplete="new-password"
            disabled={submitting}
            required
            aria-describedby="cp-next-hint"
          />
          <span id="cp-next-hint" className="form__hint">
            {NEW_HINT}
          </span>
        </div>

        <div className="login-card__field">
          <label htmlFor="cp-confirm">Confirm new password</label>
          <input
            id="cp-confirm"
            type="password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            autoComplete="new-password"
            disabled={submitting}
            required
          />
        </div>

        {matchError && (
          <div className="login-card__error" role="alert">
            {matchError}
          </div>
        )}
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
          {submitting ? 'Updating…' : 'Update password'}
        </button>
      </form>
    </div>
  );
}
