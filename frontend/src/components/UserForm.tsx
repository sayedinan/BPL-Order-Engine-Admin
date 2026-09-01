/**
 * UserForm — add or edit a user.
 *
 * Fields: username, password (create only — never pre-filled for
 * edit), role (constrained by caller's role per SPEC §3.1), and
 * assignedEngines multi-select.
 *
 * Role constraints:
 *   - ADMIN can only create USER.
 *   - SYS_ADMIN can create USER, ADMIN, or SYS_ADMIN.
 *   - The role select reflects the caller's permission.
 *
 * Edit mode: password is blank (write-only); only the new value is
 * sent on PATCH (omitted if blank).
 */
import { useState, type FormEvent } from 'react';
import { useAuth } from '../auth/useAuth';
import type {
  CreateUserRequest,
  EngineResponse,
  Role,
  UserResponse,
} from '../api/types';

interface UserFormProps {
  mode: 'create' | 'edit';
  initial?: UserResponse;
  availableEngines: EngineResponse[];
  onCancel: () => void;
  onSubmit: (body: CreateUserRequest | {
    role?: Role;
    assignedEngineCodes?: string[];
  }) => Promise<void>;
}

export function UserForm({
  mode,
  initial,
  availableEngines,
  onCancel,
  onSubmit,
}: UserFormProps) {
  const { user: caller } = useAuth();
  const [username, setUsername] = useState(initial?.username ?? '');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<Role>(initial?.role ?? 'USER');
  const [assigned, setAssigned] = useState<string[]>(
    initial?.assignedEngineCodes ?? [],
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // ADMIN cannot create or set a target role above USER.
  // SYS_ADMIN has the full list.
  const allowedRoles: Role[] =
    caller?.role === 'SYS_ADMIN' ? ['USER', 'ADMIN', 'SYS_ADMIN'] : ['USER'];

  function toggleEngine(code: string) {
    setAssigned((prev) =>
      prev.includes(code) ? prev.filter((c) => c !== code) : [...prev, code],
    );
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);

    if (mode === 'create') {
      if (!username.trim()) {
        setError('Username is required.');
        return;
      }
      if (!password) {
        setError('Password is required for new users.');
        return;
      }
      if (password.length < 12 || !/[A-Za-z]/.test(password) || !/\d/.test(password)) {
        setError(
          'Password must be at least 12 characters and include a letter and a digit.',
        );
        return;
      }
    }

    if (mode === 'create') {
      const body: CreateUserRequest = {
        username: username.trim(),
        password,
        role,
        assignedEngineCodes: assigned,
      };
      setSubmitting(true);
      try {
        await onSubmit(body);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to create user');
      } finally {
        setSubmitting(false);
      }
    } else {
      const body: {
        role?: Role;
        assignedEngineCodes?: string[];
      } = {
        role,
        assignedEngineCodes: assigned,
      };
      setSubmitting(true);
      try {
        await onSubmit(body);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to update user');
      } finally {
        setSubmitting(false);
      }
    }
  }

  return (
    <div
      className="modal-backdrop"
      onClick={(e) => {
        if (e.target === e.currentTarget) onCancel();
      }}
    >
      <form className="modal" onSubmit={handleSubmit} noValidate>
        <div className="modal__header">
          <span className="modal__title">
            {mode === 'create' ? 'Add User' : `Edit User: ${initial?.username}`}
          </span>
          <button
            type="button"
            className="modal__close"
            onClick={onCancel}
            aria-label="Close"
          >
            ×
          </button>
        </div>

        <div className="form">
          <div className="form__field">
            <label htmlFor="uf-username">Username</label>
            <input
              id="uf-username"
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="off"
              disabled={submitting || mode === 'edit'}
              required
              maxLength={64}
            />
          </div>

          {mode === 'create' && (
            <div className="form__field">
              <label htmlFor="uf-password">Initial password</label>
              <input
                id="uf-password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="new-password"
                disabled={submitting}
                required
                aria-describedby="uf-password-hint"
              />
              <span id="uf-password-hint" className="form__hint">
                The user will be required to change this on first login.
                At least 12 chars, including a letter and a digit.
              </span>
            </div>
          )}

          <div className="form__field">
            <label htmlFor="uf-role">Role</label>
            <select
              id="uf-role"
              value={role}
              onChange={(e) => setRole(e.target.value as Role)}
              disabled={submitting}
            >
              {allowedRoles.map((r) => (
                <option key={r} value={r}>
                  {r}
                </option>
              ))}
            </select>
            {caller?.role === 'ADMIN' && (
              <span className="form__hint">
                You can only create USER-role users. Ask a SYS_ADMIN to
                create admins.
              </span>
            )}
          </div>

          {role === 'USER' && (
            <div className="form__field">
              <label>Assigned engines</label>
              <div
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 4,
                  maxHeight: 160,
                  overflowY: 'auto',
                  border: '1px solid var(--color-border)',
                  borderRadius: 'var(--radius-md)',
                  padding: 8,
                }}
              >
                {availableEngines.length === 0 ? (
                  <div className="form__hint">No engines available.</div>
                ) : (
                  availableEngines.map((e) => (
                    <label
                      key={e.code}
                      style={{
                        display: 'flex',
                        gap: 8,
                        alignItems: 'center',
                        fontSize: 13,
                        color: 'var(--color-text)',
                        cursor: 'pointer',
                      }}
                    >
                      <input
                        type="checkbox"
                        checked={assigned.includes(e.code)}
                        onChange={() => toggleEngine(e.code)}
                        disabled={submitting}
                      />
                      <span>
                        <strong>{e.code}</strong> — {e.name}
                      </span>
                    </label>
                  ))
                )}
              </div>
            </div>
          )}
        </div>

        {error && (
          <div className="login-card__error" role="alert" style={{ marginTop: 14 }}>
            {error}
          </div>
        )}

        <div className="modal__footer">
          <button
            type="button"
            className="btn btn--ghost"
            onClick={onCancel}
            disabled={submitting}
          >
            Cancel
          </button>
          <button
            type="submit"
            className="btn btn--primary"
            disabled={submitting}
          >
            {submitting
              ? 'Saving…'
              : mode === 'create'
                ? 'Create user'
                : 'Save changes'}
          </button>
        </div>
      </form>
    </div>
  );
}
