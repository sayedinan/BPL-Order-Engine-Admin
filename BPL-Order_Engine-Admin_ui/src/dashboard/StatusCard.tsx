import { useEffect, useState } from 'react';
import type { AuthState } from '../auth/types';
import { fetchStatus, type EngineStatus, type EngineStatusResponse } from '../api/api';
import { usePolling } from '../hooks/usePolling';

interface StatusCardProps {
  auth: AuthState;
  /**
   * Increment to force an immediate refresh — used by the Control
   * Panel after a successful start/stop so the Status badge updates
   * without waiting up to 3 s for the next tick.
   */
  refreshTick: number;
}

const STATUS_LABEL: Record<EngineStatus, string> = {
  RUNNING: 'RUNNING',
  STOPPED: 'STOPPED',
  ERROR: 'ERROR',
};

function formatTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleTimeString();
  } catch {
    return iso;
  }
}

/**
 * Top-left card. Auto-refreshes every 3 s (per the prompt). Renders a
 * RUNNING (green) / STOPPED (red) / ERROR (yellow) badge.
 */
export function StatusCard({ auth, refreshTick }: StatusCardProps) {
  const [data, setData] = useState<EngineStatusResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  // 3 s auto-refresh.
  usePolling(
    async (signal) => {
      const next = await fetchStatus(auth, signal);
      setData(next);
      setError(null);
    },
    3000,
    true,
    (err) => {
      if (err instanceof DOMException && err.name === 'AbortError') return;
      setError(err instanceof Error ? err.message : 'Failed to load status');
    }
  );

  // Force-refresh trigger from the parent (post start/stop).
  // The tick prop changes → this effect runs → we fire one fetch.
  useEffect(() => {
    if (refreshTick === 0) return; // skip the initial mount
    const controller = new AbortController();
    fetchStatus(auth, controller.signal)
      .then((next) => {
        setData(next);
        setError(null);
      })
      .catch((err) => {
        if (err instanceof DOMException && err.name === 'AbortError') return;
        setError(err instanceof Error ? err.message : 'Failed to load status');
      });
    return () => controller.abort();
  }, [refreshTick, auth]);

  return (
    <section className="card" aria-labelledby="status-card-title">
      <div className="card__header">
        <span className="card__title" id="status-card-title">
          Engine Status
        </span>
        {data && <span className="card__subtitle">{data.displayName}</span>}
      </div>

      {data ? (
        <>
          <div className="status-card__engine">{data.displayName}</div>
          <div className="status-card__state">
            <span
              className={`status-badge status-badge--${data.status.toLowerCase()}`}
              data-testid="status-badge"
            >
              <span className="status-badge__dot" aria-hidden="true" />
              {STATUS_LABEL[data.status]}
            </span>
            <span className="card__subtitle">
              engine id: <code>{data.engineId}</code>
            </span>
          </div>
          <dl className="status-card__meta">
            <dt>Last transition</dt>
            <dd>{formatTime(data.lastTransitionAt)}</dd>
            <dt>Last checked</dt>
            <dd>{formatTime(data.checkedAt)}</dd>
          </dl>
        </>
      ) : error ? (
        <div className="control-panel__error" role="alert">
          Could not load status: {error}
        </div>
      ) : (
        <div className="card__subtitle">Loading status…</div>
      )}
    </section>
  );
}
