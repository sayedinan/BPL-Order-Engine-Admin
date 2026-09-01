/**
 * EngineLogsTable — the streaming log view for one engine.
 *
 * Receives `lines` (already merged: the initial /logs?limit=100
 * snapshot + every line pushed by the WebSocket) and renders them in
 * a terminal-style list. Auto-scrolls to the bottom only if the user
 * is already pinned there.
 *
 * The status badge at the top reflects the WebSocket connection state
 * (open / reconnecting / closed) and the close reason when terminal.
 */
import { useEffect, useRef } from 'react';
import type { WsStatus } from '../hooks/useEngineLogsSocket';

interface EngineLogsTableProps {
  lines: { timestamp: string; level: string; message: string }[];
  wsStatus: WsStatus;
  closeReason: string | null;
  engineCode: string;
}

function formatTime(iso: string): string {
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

const STATUS_LABEL: Record<WsStatus, string> = {
  open: 'Live',
  connecting: 'Connecting…',
  reconnecting: 'Reconnecting…',
  closed: 'Disconnected',
};

export function EngineLogsTable({
  lines,
  wsStatus,
  closeReason,
  engineCode,
}: EngineLogsTableProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const lastCountRef = useRef(0);

  // Auto-scroll to bottom only if the user is already pinned there.
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const distanceFromBottom =
      el.scrollHeight - el.scrollTop - el.clientHeight;
    if (distanceFromBottom < 60) {
      el.scrollTop = el.scrollHeight;
    }
    lastCountRef.current = lines.length;
  }, [lines.length]);

  return (
    <section className="card card--full" aria-labelledby="engine-logs-title">
      <div className="card__header">
        <span className="card__title" id="engine-logs-title">
          Engine execution logs
        </span>
        <div className="card__subtitle">
          {engineCode} · {STATUS_LABEL[wsStatus]}
          {closeReason && wsStatus === 'closed' ? ` (${closeReason})` : ''}
        </div>
      </div>

      {lines.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state__title">No log lines yet</div>
          <div>
            The engine is stopped, or the WebSocket hasn&apos;t delivered
            any lines yet.
          </div>
        </div>
      ) : (
        <div
          className="log-terminal"
          ref={containerRef}
          role="log"
          aria-live="polite"
          aria-relevant="additions"
        >
          {lines.map((l, i) => (
            <div className="log-line" key={`${l.timestamp}-${i}`}>
              <span className="log-line__time">{formatTime(l.timestamp)}</span>
              <span className={`log-line__level log-line__level--${l.level.toUpperCase()}`}>
                {l.level}
              </span>
              <span className="log-line__msg">{l.message}</span>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
