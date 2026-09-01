/**
 * EngineForm — add or edit an engine.
 *
 * Fields: name, code, mode, serverIp, serverUsername, serverPassword
 * (write-only), startScript, stopScript, logScript.
 *
 * Security: serverPassword is NEVER pre-filled from the existing
 * engine (the field doesn't exist on EngineResponse). The user must
 * retype it to change it. The component calls `onSubmit` with the
 * full body; the parent decides whether to POST or PATCH.
 */
import { useState, type FormEvent } from 'react';
import type {
  CreateEngineRequest,
  EngineMode,
  EngineResponse,
  UpdateEngineSshRequest,
} from '../api/types';

interface EngineFormProps {
  mode: 'create' | 'edit';
  initial?: EngineResponse;
  onCancel: () => void;
  onSubmit: (
    body: CreateEngineRequest | UpdateEngineSshRequest,
  ) => Promise<void>;
}

export function EngineForm({ mode, initial, onCancel, onSubmit }: EngineFormProps) {
  const [name, setName] = useState(initial?.name ?? '');
  const [code, setCode] = useState(initial?.code ?? '');
  const [engineMode, setEngineMode] = useState<EngineMode>(initial?.mode ?? 'MOCK');
  const [serverIp, setServerIp] = useState(initial?.serverIp ?? '');
  const [serverUsername, setServerUsername] = useState(
    initial?.serverUsername ?? '',
  );
  const [serverPassword, setServerPassword] = useState('');
  const [startScript, setStartScript] = useState(initial?.startScript ?? '');
  const [stopScript, setStopScript] = useState(initial?.stopScript ?? '');
  const [logScript, setLogScript] = useState(initial?.logScript ?? '');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);

    if (mode === 'create') {
      if (!name || !code || !serverIp || !serverUsername || !serverPassword) {
        setError('Please complete all required fields.');
        return;
      }
    } else if (!serverIp || !serverUsername) {
      setError('Server IP and username are required.');
      return;
    }

    setSubmitting(true);
    try {
      if (mode === 'create') {
        const body: CreateEngineRequest = {
          name,
          code,
          mode: engineMode,
          serverIp,
          serverUsername,
          serverPassword,
          startScript: startScript || undefined,
          stopScript: stopScript || undefined,
          logScript: logScript || undefined,
        };
        await onSubmit(body);
      } else {
        // Edit: only send fields that are set. serverPassword is only
        // sent if the user retyped it (left empty = "no change").
        const body: UpdateEngineSshRequest = {
          name,
          mode: engineMode,
          serverIp,
          serverUsername,
          ...(serverPassword ? { serverPassword } : {}),
          startScript: startScript || undefined,
          stopScript: stopScript || undefined,
          logScript: logScript || undefined,
        };
        await onSubmit(body);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save engine');
    } finally {
      setSubmitting(false);
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
            {mode === 'create' ? 'Add Engine' : `Edit Engine: ${initial?.code}`}
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
          {mode === 'create' && (
            <div className="form__field">
              <label htmlFor="ef-code">Engine code</label>
              <input
                id="ef-code"
                type="text"
                value={code}
                onChange={(e) => setCode(e.target.value.toUpperCase())}
                pattern="^[A-Z0-9_]{2,16}$"
                disabled={submitting}
                required
                aria-describedby="ef-code-hint"
              />
              <span id="ef-code-hint" className="form__hint">
                2–16 chars: A–Z, 0–9, underscore. Unique among engines.
              </span>
            </div>
          )}

          <div className="form__field">
            <label htmlFor="ef-name">Display name</label>
            <input
              id="ef-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              maxLength={80}
              disabled={submitting}
              required
            />
          </div>

          <div className="form__field">
            <label htmlFor="ef-mode">Mode</label>
            <select
              id="ef-mode"
              value={engineMode}
              onChange={(e) => setEngineMode(e.target.value as EngineMode)}
              disabled={submitting}
            >
              <option value="MOCK">MOCK</option>
              <option value="REAL">REAL (SSH)</option>
            </select>
          </div>

          <div className="form__row">
            <div className="form__field" style={{ flex: 2 }}>
              <label htmlFor="ef-ip">Server IP / hostname</label>
              <input
                id="ef-ip"
                type="text"
                value={serverIp}
                onChange={(e) => setServerIp(e.target.value)}
                disabled={submitting}
                required
              />
            </div>
            <div className="form__field" style={{ flex: 1 }}>
              <label htmlFor="ef-user">SSH username</label>
              <input
                id="ef-user"
                type="text"
                value={serverUsername}
                onChange={(e) => setServerUsername(e.target.value)}
                disabled={submitting}
                required
              />
            </div>
          </div>

          <div className="form__field">
            <label htmlFor="ef-pass">Server password (write-only)</label>
            <input
              id="ef-pass"
              type="password"
              value={serverPassword}
              onChange={(e) => setServerPassword(e.target.value)}
              autoComplete="new-password"
              disabled={submitting}
              {...(mode === 'create' ? { required: true } : {})}
              aria-describedby="ef-pass-hint"
            />
            <span id="ef-pass-hint" className="form__hint">
              {mode === 'create'
                ? 'Jasypt-encrypted at rest. Never returned in responses.'
                : 'Leave blank to keep the existing password. Type a new value to change it.'}
            </span>
          </div>

          <div className="form__field">
            <label htmlFor="ef-start">Start script</label>
            <input
              id="ef-start"
              type="text"
              value={startScript ?? ''}
              onChange={(e) => setStartScript(e.target.value)}
              disabled={submitting}
              placeholder="systemctl start bpl-engine"
            />
          </div>

          <div className="form__field">
            <label htmlFor="ef-stop">Stop script</label>
            <input
              id="ef-stop"
              type="text"
              value={stopScript ?? ''}
              onChange={(e) => setStopScript(e.target.value)}
              disabled={submitting}
              placeholder="systemctl stop bpl-engine"
            />
          </div>

          <div className="form__field">
            <label htmlFor="ef-log">Log script (tail -F …)</label>
            <input
              id="ef-log"
              type="text"
              value={logScript ?? ''}
              onChange={(e) => setLogScript(e.target.value)}
              disabled={submitting}
              placeholder="tail -F /var/log/bpl.log"
            />
          </div>
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
                ? 'Create engine'
                : 'Save changes'}
          </button>
        </div>
      </form>
    </div>
  );
}
