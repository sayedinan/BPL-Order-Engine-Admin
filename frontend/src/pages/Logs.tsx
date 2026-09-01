/**
 * Logs page.
 *
 * Two view modes (Source dropdown):
 *   - "System Audit Logs" (default, hidden for USER) — every audit
 *     row, paginated, newest first. No filters: each row already
 *     shows who, what, which engine, when, and details. Filtering by
 *     actor/action/engine in the UI added noise without value.
 *   - "Engine Execution Logs" (always available) — pick one engine
 *     from the visible list, see its log lines live.
 *
 * The URL stores `source` and `engine` (so the back button works and
 * the engine source is shareable) plus `page` for the audit log.
 */
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { auditApi, enginesApi } from '../api/client';
import type { AuditLogResponse, EngineResponse } from '../api/types';
import { AuditLogsTable } from '../components/AuditLogsTable';
import { EngineLogsTable } from '../components/EngineLogsTable';
import { useEngineLogsSocket } from '../hooks/useEngineLogsSocket';

type Source = 'audit' | 'engine';

export function Logs() {
  const { user } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const [engines, setEngines] = useState<EngineResponse[]>([]);

  // Derive state from URL so the back button works.
  const source = (searchParams.get('source') as Source | null) ?? 'audit';
  const isUser = user?.role === 'USER';
  // Defense in depth: even if the URL says audit, USER can't see audit.
  const effectiveSource: Source = isUser ? 'engine' : source;
  const engineFromUrl = searchParams.get('engine') ?? '';
  const page = Math.max(0, Number(searchParams.get('page') ?? '0'));
  const size = 50;

  // Fetch the visible engines so the Engine dropdown is populated.
  useEffect(() => {
    let cancelled = false;
    enginesApi
      .list()
      .then((list) => {
        if (cancelled) return;
        setEngines(list);
        // If no engine is selected and the list is non-empty, pick the
        // first one for the default view.
        if (!engineFromUrl && list.length > 0) {
          const next = new URLSearchParams(searchParams);
          next.set('engine', list[0].code);
          setSearchParams(next, { replace: true });
        }
      })
      .catch(() => {
        /* error surfaced inline in the engine section */
      });
    return () => {
      cancelled = true;
    };
    // We intentionally exclude `engineFromUrl` etc. from deps to
    // avoid re-fetching on every URL change. The list is fetched once
    // on mount.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const visibleEngines =
    user?.role === 'USER'
      ? engines.filter((e) => user.assignedEngineCodes.includes(e.code))
      : engines;

  // Derive the active engine from URL or the first visible.
  const activeEngine =
    visibleEngines.find((e) => e.code === engineFromUrl) ??
    visibleEngines[0] ??
    null;

  function setParam(key: string, value: string | null) {
    const next = new URLSearchParams(searchParams);
    if (value === null || value === '') next.delete(key);
    else next.set(key, value);
    // Reset page on filter change.
    if (key !== 'page') next.delete('page');
    setSearchParams(next, { replace: true });
  }

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Logs</h1>
          <div className="page-header__subtitle">
            {effectiveSource === 'audit'
              ? 'System audit trail (who did what).'
              : 'Engine execution logs (what the engine printed).'}
          </div>
        </div>
      </div>

      <div className="filter-bar">
        <label className="filter-bar__group">
          Source:
          <select
            value={effectiveSource}
            onChange={(e) => setParam('source', e.target.value)}
            disabled={isUser}
          >
            {!isUser && <option value="audit">System Audit Logs</option>}
            <option value="engine">Engine Execution Logs</option>
          </select>
        </label>

        {effectiveSource === 'engine' && (
          <label className="filter-bar__group">
            Engine:
            <select
              value={activeEngine?.code ?? ''}
              onChange={(e) => setParam('engine', e.target.value)}
              disabled={visibleEngines.length === 0}
            >
              {visibleEngines.length === 0 ? (
                <option value="">No engines</option>
              ) : (
                visibleEngines.map((e) => (
                  <option key={e.code} value={e.code}>
                    {e.code} — {e.name}
                  </option>
                ))
              )}
            </select>
          </label>
        )}
      </div>

      {effectiveSource === 'audit' && !isUser && (
        <AuditLogsView
          page={page}
          size={size}
          onPageChange={(p) => setParam('page', String(p))}
        />
      )}

      {effectiveSource === 'engine' && activeEngine && (
        <EngineLogsView engineCode={activeEngine.code} />
      )}

      {effectiveSource === 'engine' && !activeEngine && (
        <div className="empty-state">
          <div className="empty-state__title">No engine selected</div>
          {user?.role === 'USER' && user.assignedEngineCodes.length === 0 ? (
            <div>You have no engines assigned.</div>
          ) : (
            <div>Pick an engine above to view its logs.</div>
          )}
        </div>
      )}
    </>
  );
}

// ---- Sub-views (kept inline; they own their own loading state) ----

interface AuditLogsViewProps {
  page: number;
  size: number;
  onPageChange: (page: number) => void;
}

function AuditLogsView({ page, size, onPageChange }: AuditLogsViewProps) {
  const [rows, setRows] = useState<AuditLogResponse[]>([]);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    // Don't set loading=true here (lint rule). The initial state is
    // already true; on page change we keep the previous rows visible
    // until the new ones arrive.
    auditApi
      .list({ page, size })
      .then((res) => {
        if (cancelled) return;
        setRows(res.items);
        setTotal(res.total);
        setError(null);
        setLoading(false);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(err instanceof Error ? err.message : 'Failed to load audit log');
        setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [page, size]);

  if (error) {
    return (
      <div className="control-panel__error" role="alert">
        {error}
      </div>
    );
  }
  if (loading && rows.length === 0) {
    return (
      <div className="empty-state">
        <div className="empty-state__title">Loading audit log…</div>
      </div>
    );
  }
  return (
    <AuditLogsTable
      rows={rows}
      page={page}
      size={size}
      total={total}
      onPageChange={onPageChange}
    />
  );
}

interface EngineLogsViewProps {
  engineCode: string;
}

function EngineLogsView({ engineCode }: EngineLogsViewProps) {
  // The hook gives us the live line list. The initial snapshot is
  // already pushed by MockLogsSocket on open, so we don't need to
  // call GET /api/engines/{code}/logs here — the WS snapshot is
  // sufficient for the mock and the real backend does the same.
  const { lines, status, closeReason } = useEngineLogsSocket(engineCode);

  return (
    <EngineLogsTable
      lines={lines}
      wsStatus={status}
      closeReason={closeReason}
      engineCode={engineCode}
    />
  );
}
