/**
 * AuditLogsTable — paginated table of audit log rows.
 *
 * Columns: timestamp, actorUsername, action, targetEngineCode,
 * details (raw-JSON toggle).
 *
 * Renders rows that come from /api/audit-logs. The pagination controls
 * (Prev/Next) and the per-page size are owned by the parent (Logs
 * page) so the URL query params can drive them.
 */
import { useState } from 'react';
import type { AuditLogResponse } from '../api/types';

interface AuditLogsTableProps {
  rows: AuditLogResponse[];
  page: number;
  size: number;
  total: number;
  onPageChange: (page: number) => void;
}

function formatTime(iso: string): string {
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

export function AuditLogsTable({
  rows,
  page,
  size,
  total,
  onPageChange,
}: AuditLogsTableProps) {
  const [showRaw, setShowRaw] = useState(false);
  const totalPages = Math.max(1, Math.ceil(total / size));
  const canPrev = page > 0;
  const canNext = page + 1 < totalPages;

  if (rows.length === 0) {
    return (
      <div className="empty-state">
        <div className="empty-state__title">No audit log rows</div>
        <div>The audit log is empty so far.</div>
      </div>
    );
  }

  return (
    <>
      <div className="filter-bar" style={{ marginBottom: 8 }}>
        <label className="filter-bar__group">
          <input
            type="checkbox"
            checked={showRaw}
            onChange={(e) => setShowRaw(e.target.checked)}
          />
          View raw JSON
        </label>
        <div className="filter-bar__group" style={{ marginLeft: 'auto' }}>
          <span>
            Page {page + 1} of {totalPages} · {total} total
          </span>
        </div>
      </div>
      <table className="data-table">
        <thead>
          <tr>
            <th>Timestamp</th>
            <th>Actor</th>
            <th>Action</th>
            <th>Engine</th>
            <th>Details</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id}>
              <td>{formatTime(r.timestamp)}</td>
              <td>
                <div>{r.actorUsername}</div>
                <div className="data-table__code">{r.actorRole}</div>
              </td>
              <td className="data-table__code">{r.action}</td>
              <td className="data-table__code">
                {r.targetEngineCode ?? '—'}
              </td>
              <td>
                {showRaw ? (
                  <pre
                    style={{
                      margin: 0,
                      fontFamily: 'var(--font-mono)',
                      fontSize: 12,
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-word',
                      color: 'var(--color-text-muted)',
                    }}
                  >
                    {JSON.stringify(r.details, null, 2)}
                  </pre>
                ) : (
                  <code className="data-table__code">
                    {JSON.stringify(r.details)}
                  </code>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <div
        className="filter-bar"
        style={{ marginTop: 12, justifyContent: 'flex-end' }}
      >
        <button
          type="button"
          className="btn"
          onClick={() => onPageChange(page - 1)}
          disabled={!canPrev}
        >
          ← Prev
        </button>
        <button
          type="button"
          className="btn"
          onClick={() => onPageChange(page + 1)}
          disabled={!canNext}
        >
          Next →
        </button>
      </div>
    </>
  );
}
