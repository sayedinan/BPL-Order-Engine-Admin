/**
 * Dashboard — the engine grid.
 *
 * - Fetches `GET /api/engines` on mount.
 * - USER sees only their `assignedEngines`; the server already filters,
 *   and we re-filter client-side as defense in depth.
 * - ADMIN/SYS_ADMIN see all.
 * - SYS_ADMIN sees a "+ Add Engine" button that opens the EngineForm
 *   modal.
 *
 * After a successful create the list refreshes; the new card appears
 * in the grid.
 */
import { useEffect, useState } from 'react';
import { useAuth } from '../auth/useAuth';
import { enginesApi } from '../api/client';
import type { CreateEngineRequest, EngineResponse } from '../api/types';
import { EngineCard } from '../components/EngineCard';
import { EngineForm } from '../components/EngineForm';

export function Dashboard() {
  const { user } = useAuth();
  const [engines, setEngines] = useState<EngineResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [showAdd, setShowAdd] = useState(false);
  // Bump to force a re-fetch (e.g. after creating a new engine).
  const [reloadTick, setReloadTick] = useState(0);

  useEffect(() => {
    let cancelled = false;
    // We start "loading" via useState's initial value; here we only
    // update state on resolution. The pattern avoids calling setState
    // synchronously in the effect body.
    enginesApi
      .list()
      .then((list) => {
        if (cancelled) return;
        setEngines(list);
        setError(null);
        setLoading(false);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(err instanceof Error ? err.message : 'Failed to load engines');
        setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [reloadTick]);

  // Defense-in-depth filter: even if the server returns unassigned
  // engines, the USER never sees them. The server is the source of
  // truth, but this prevents a flash of unassigned content during a
  // role transition.
  const visibleEngines =
    user?.role === 'USER'
      ? engines.filter((e) => user.assignedEngineCodes.includes(e.code))
      : engines;

  const isSysAdmin = user?.role === 'SYS_ADMIN';

  async function handleCreate(body: CreateEngineRequest | unknown) {
    // The EngineForm is only used in 'create' mode here, so the body
    // is always a CreateEngineRequest. Cast is safe by construction.
    await enginesApi.create(body as CreateEngineRequest);
    setShowAdd(false);
    setLoading(true);
    setReloadTick((t) => t + 1);
  }

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Dashboard</h1>
          <div className="page-header__subtitle">
            {user?.role === 'USER'
              ? `Engines assigned to ${user.username}.`
              : 'All engines.'}
          </div>
        </div>
        {isSysAdmin && (
          <button
            type="button"
            className="btn btn--primary"
            onClick={() => setShowAdd(true)}
          >
            + Add Engine
          </button>
        )}
      </div>

      {error && (
        <div className="control-panel__error" role="alert">
          {error}
        </div>
      )}

      {loading ? (
        <div className="empty-state">
          <div className="empty-state__title">Loading engines…</div>
        </div>
      ) : visibleEngines.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state__title">No engines visible</div>
          {user?.role === 'USER' ? (
            <div>
              You have no engines assigned. Ask a SYS_ADMIN to assign
              you to one.
            </div>
          ) : isSysAdmin ? (
            <div>Click &ldquo;+ Add Engine&rdquo; to add your first one.</div>
          ) : (
            <div>No engines are registered.</div>
          )}
        </div>
      ) : (
        <div className="dashboard-grid">
          {visibleEngines.map((e) => (
            <EngineCard key={e.code} engine={e} />
          ))}
        </div>
      )}

      {showAdd && (
        <EngineForm
          mode="create"
          onCancel={() => setShowAdd(false)}
          onSubmit={async (body) => {
            await handleCreate(body);
          }}
        />
      )}
    </>
  );
}
