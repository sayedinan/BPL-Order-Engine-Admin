/**
 * EngineCard — one card per engine on the dashboard.
 *
 * Shows: name, code, status pill, last transition, [Start] [Stop]
 * [View Logs] actions. Status is live (useEngineStatus polls every
 * 5s). Start/Stop buttons are role-gated (USER can act on assigned
 * engines only) and disable themselves during in-flight calls.
 *
 * Errors surface inline (the api/client envelope unwrap gives us a
 * plain Error.message).
 */
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { enginesApi } from '../api/client';
import type { EngineResponse } from '../api/types';
import { useEngineStatus } from '../hooks/useEngineStatus';
import { StatusPill } from './StatusPill';

interface EngineCardProps {
  engine: EngineResponse;
}

function formatTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleTimeString();
  } catch {
    return iso;
  }
}

export function EngineCard({ engine }: EngineCardProps) {
  const { user } = useAuth();
  const { data, refresh } = useEngineStatus(engine.code, true);
  const [action, setAction] = useState<'start' | 'stop' | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const status = data?.status ?? engine.status;
  const lastTransitionAt = data?.lastTransitionAt ?? engine.lastTransitionAt;

  // USER can act only on assigned engines. ADMIN/SYS_ADMIN can act on
  // all visible. The dashboard's filter already hides unassigned
  // engines, but we keep the check here as defense in depth.
  const canAct =
    user &&
    (user.role === 'SYS_ADMIN' ||
      user.role === 'ADMIN' ||
      (user.role === 'USER' && user.assignedEngineCodes.includes(engine.code)));

  const canStart = canAct && status !== 'RUNNING' && status !== 'ERROR';
  const canStop = canAct && status === 'RUNNING';

  async function doAction(kind: 'start' | 'stop') {
    if (!canAct) return;
    setError(null);
    setSuccess(null);
    setAction(kind);
    try {
      const res =
        kind === 'start' ? await enginesApi.start(engine.code) : await enginesApi.stop(engine.code);
      setSuccess(res.message);
      // Force an immediate refresh so the badge updates without
      // waiting up to 5s.
      refresh();
      setTimeout(() => setSuccess(null), 3000);
    } catch (err) {
      if (err instanceof Error && err.name === 'AbortError') return;
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError(`Could not ${kind} engine`);
      }
    } finally {
      setAction(null);
    }
  }

  return (
    <section className="card" aria-labelledby={`engine-${engine.code}-title`}>
      <div className="card__header">
        <div>
          <span className="card__title" id={`engine-${engine.code}-title`}>
            {engine.name}
          </span>
          <div className="data-table__code" style={{ marginTop: 2 }}>
            code: {engine.code} · {engine.mode}
          </div>
        </div>
        <StatusPill status={status} />
      </div>

      <dl className="status-card__meta">
        <dt>Last transition</dt>
        <dd>{formatTime(lastTransitionAt)}</dd>
        <dt>Server</dt>
        <dd>
          {engine.serverUsername}@{engine.serverIp}
        </dd>
      </dl>

      {canAct && (
        <div className="control-panel__row">
          <button
            type="button"
            className="btn btn--success"
            disabled={!canStart || action !== null}
            onClick={() => doAction('start')}
            aria-label={`Start ${engine.name}`}
          >
            {action === 'start' ? 'Starting…' : 'Start'}
          </button>
          <button
            type="button"
            className="btn btn--danger"
            disabled={!canStop || action !== null}
            onClick={() => doAction('stop')}
            aria-label={`Stop ${engine.name}`}
          >
            {action === 'stop' ? 'Stopping…' : 'Stop'}
          </button>
          <Link
            to={`/logs?engine=${encodeURIComponent(engine.code)}`}
            className="btn"
          >
            View Logs
          </Link>
        </div>
      )}

      {!canAct && (
        <div className="control-panel__notice" role="status">
          You don&apos;t have permission to control this engine.
        </div>
      )}

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
