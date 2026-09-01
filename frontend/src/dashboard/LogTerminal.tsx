import { useEffect, useRef, useState } from 'react';
import type { AuthState } from '../auth/types';
import { fetchLogs, type LogLine, type LogPageResponse } from '../api/api';
import { usePolling } from '../hooks/usePolling';

interface LogTerminalProps {
  auth: AuthState;
}

type Limit = 50 | 100 | 200;

const LIMITS: Limit[] = [50, 100, 200];

function formatTime(iso: string): string {
  // Compact HH:MM:SS.mmm — the full ISO-8601 string is too wide for a
  // terminal-style log line.
  try {
    const d = new Date(iso);
    const hh = String(d.getHours()).padStart(2, '0');
    const mm = String(d.getMinutes()).padStart(2, '0');
    const ss = String(d.getSeconds()).padStart(2, '0');
    const ms = String(d.getMilliseconds()).padStart(3, '0');
    return `${hh}:${mm}:${ss}.${ms}`;
  } catch {
    return iso;
  }
}

function levelClass(level: string): string {
  const upper = level.toUpperCase();
  return `log-line__level log-line__level--${upper}`;
}

/**
 * Auto-scrolling log console. Polls the backend at a slower cadence
 * (5 s) than the Status card so the two polls don't compete; the
 * prompt asked for a "live logs terminal" so we keep it automatic
 * but not the same 3 s as the Status.
 *
 * Auto-scroll only kicks in if the user is already pinned to the
 * bottom — if they've scrolled up to read older lines, we don't
 * yank them back down.
 */
export function LogTerminal({ auth }: LogTerminalProps) {
  const [page, setPage] = useState<LogPageResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [limit, setLimit] = useState<Limit>(100);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const lastCountRef = useRef(0);

  usePolling(
    async (signal) => {
      const next = await fetchLogs(auth, limit, signal);
      setPage(next);
      lastCountRef.current = next.count;
      setError(null);
    },
    5000,
    true,
    (err) => {
      if (err instanceof DOMException && err.name === 'AbortError') return;
      setError(err instanceof Error ? err.message : 'Failed to load logs');
    }
  );

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    // Only auto-scroll if the user is already at (or near) the bottom.
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    if (distanceFromBottom < 60) {
      el.scrollTop = el.scrollHeight;
    }
  }, [page]);

  const lines: LogLine[] = page?.lines ?? [];

  return (
    <section className="card card--full" aria-labelledby="logs-title">
      <div className="card__header">
        <span className="card__title" id="logs-title">
          Live Logs
        </span>
        <div className="log-terminal__footer" style={{ marginTop: 0 }}>
          <label>
            Limit:&nbsp;
            <select
              value={limit}
              onChange={(e) => setLimit(Number(e.target.value) as Limit)}
              style={{
                background: 'var(--color-bg)',
                color: 'var(--color-text)',
                border: '1px solid var(--color-border-strong)',
                borderRadius: 6,
                padding: '4px 8px',
                fontFamily: 'inherit',
                fontSize: 12,
              }}
            >
              {LIMITS.map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </select>
          </label>
          <span>
            {page ? `${page.count} line${page.count === 1 ? '' : 's'}` : '…'}
          </span>
        </div>
      </div>

      {error ? (
        <div className="control-panel__error" role="alert">
          Could not load logs: {error}
        </div>
      ) : (
        <div
          className="log-terminal"
          ref={containerRef}
          role="log"
          aria-live="polite"
          aria-relevant="additions"
        >
          {lines.length === 0 ? (
            <div className="log-terminal__empty">No log lines yet — engine is STOPPED.</div>
          ) : (
            lines.map((l, i) => (
              <div className="log-line" key={`${l.timestamp}-${i}`}>
                <span className="log-line__time">{formatTime(l.timestamp)}</span>
                <span className={levelClass(l.level)}>{l.level}</span>
                <span className="log-line__msg">{l.message}</span>
              </div>
            ))
          )}
        </div>
      )}
    </section>
  );
}
