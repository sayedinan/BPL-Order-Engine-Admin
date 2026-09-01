import { useState } from 'react';
import type { AuthState } from '../auth/types';
import { ApiError, startEngine, stopEngine, type EngineStatus } from '../api/api';

interface ControlPanelProps {
  auth: AuthState;
  status: EngineStatus | null;
  /**
   * Inform the parent that the engine state changed. We pass the
   * new status string (not the full DTO) because the action response
   * shape differs from the status response; the parent's StatusCard
   * will fetch a fresh full status on the next tick anyway.
   */
  onStatusChanged: (newStatus: EngineStatus) => void;
}

type ActionKind = 'start' | 'stop';

/**
 * Right column on the top row. ADMIN-only: the Start/Stop buttons are
 * disabled and carry a tooltip explaining why when the signed-in
 * user is a VIEWER. We don't even attempt the network call in that
 * case so the API returns nothing misleading.
 */
export function ControlPanel({ auth, status, onStatusChanged }: ControlPanelProps) {
  const [busy, setBusy] = useState<ActionKind | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [showStopConfirm, setShowStopConfirm] = useState(false);

  const isAdmin = auth.role === 'ADMIN';
  // Disable Start when already RUNNING (or ERROR — needs manual recovery); Stop only when STOPPED.
  const canStart = isAdmin && status !== null && status !== 'RUNNING' && status !== 'ERROR';
  const canStop = isAdmin && status === 'RUNNING';

  const viewerTooltip = 'Viewer role cannot control the engine — sign in as Admin to start/stop.';

  async function doAction(kind: ActionKind) {
    setError(null);
    setSuccess(null);
    setBusy(kind);
    try {
      const result = kind === 'start' ? await startEngine(auth) : await stopEngine(auth);
      onStatusChanged(result.status);
      setSuccess(result.message);
      // Auto-dismiss success after 3 s (matches SPEC §4.2).
      setTimeout(() => setSuccess(null), 3000);
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') return;
      if (err instanceof ApiError) {
        if (err.status === 409) {
          setError('Already in that state — refreshing status.');
        } else if (err.status === 403) {
          setError("You don't have permission to do that.");
        } else {
          setError(`Could not ${kind} engine: ${err.message}`);
        }
      } else {
        setError(`Could not ${kind} engine: ${err instanceof Error ? err.message : 'unknown error'}`);
      }
    } finally {
      setBusy(null);
      setShowStopConfirm(false);
    }
  }

  function handleStart() {
    void doAction('start');
  }

  function handleStopClick() {
    // SPEC §4.2 says Stop requires confirmation. We use the native
    // confirm() to keep dependencies minimal; this is a small internal
    // tool, not a customer-facing product.
    if (window.confirm('Stop BPL Order Engine? Polling and logs will pause until you start it again.')) {
      void doAction('stop');
    }
  }
  // Suppress unused-binding warning while keeping the show-state for future UX.
  void showStopConfirm;

  return (
    <section className="card" aria-labelledby="control-panel-title">
      <div className="card__header">
        <span className="card__title" id="control-panel-title">
          Control Panel
        </span>
        {status && <span className="card__subtitle">current: {status}</span>}
      </div>

      {!isAdmin && (
        <div className="control-panel__notice" role="status">
          You don't have permission to control the engine. Sign in as Admin to enable Start/Stop.
        </div>
      )}

      <div className="control-panel__row">
        <div
          className="tooltip-wrapper"
          data-tooltip={!isAdmin ? viewerTooltip : status === 'RUNNING' ? 'Engine is already running' : status === 'ERROR' ? 'Recover from ERROR before starting' : undefined}
        >
          <button
            type="button"
            className="btn btn--success"
            disabled={!canStart || busy !== null}
            onClick={handleStart}
            aria-label="Start engine"
          >
            {busy === 'start' ? 'Starting…' : 'Start Engine'}
          </button>
        </div>
        <div
          className="tooltip-wrapper"
          data-tooltip={!isAdmin ? viewerTooltip : status !== 'RUNNING' ? 'Engine is not running' : undefined}
        >
          <button
            type="button"
            className="btn btn--danger"
            disabled={!canStop || busy !== null}
            onClick={handleStopClick}
            aria-label="Stop engine"
          >
            {busy === 'stop' ? 'Stopping…' : 'Stop Engine'}
          </button>
        </div>
      </div>

      {error && (
        <div className="control-panel__error" role="alert">
          {error}
        </div>
      )}
      {success && (
        <div className="control-panel__success" role="status">
          {success}
        </div>
      )}
    </section>
  );
}
