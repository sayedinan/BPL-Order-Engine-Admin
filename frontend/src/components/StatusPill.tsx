/**
 * StatusPill — color-coded pill for an engine's status.
 *
 * RUNNING = green (pulsing dot), STOPPED = gray, ERROR = yellow.
 */
import type { EngineStatus } from '../api/types';

interface StatusPillProps {
  status: EngineStatus;
}

const LABEL: Record<EngineStatus, string> = {
  RUNNING: 'RUNNING',
  STOPPED: 'STOPPED',
  ERROR: 'ERROR',
};

const CLASS: Record<EngineStatus, string> = {
  RUNNING: 'status-badge status-badge--running',
  STOPPED: 'status-badge status-badge--stopped',
  ERROR: 'status-badge status-badge--error',
};

export function StatusPill({ status }: StatusPillProps) {
  return (
    <span
      className={CLASS[status]}
      data-testid={`status-pill-${status.toLowerCase()}`}
    >
      <span className="status-badge__dot" aria-hidden="true" />
      {LABEL[status]}
    </span>
  );
}
