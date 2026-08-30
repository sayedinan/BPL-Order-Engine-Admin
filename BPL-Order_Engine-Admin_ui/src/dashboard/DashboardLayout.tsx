import { useState, useCallback } from 'react';
import { useAuth } from '../auth/useAuth';
import { RoleBadge } from './RoleBadge';
import { StatusCard } from './StatusCard';
import { ControlPanel } from './ControlPanel';
import { LogTerminal } from './LogTerminal';
import type { EngineStatus, EngineStatusResponse } from '../api/api';

/**
 * Authenticated shell. Holds the shared auth-derived state, the
 * refresh counter, and the local "current status" copy that the
 * Control Panel uses to enable/disable its buttons. StatusCard is
 * the source of truth; ControlPanel informs the parent of changes
 * via {@code onStatusChanged} so the local copy stays in sync
 * without a second polling timer.
 */
export function DashboardLayout() {
  const { auth, signOut } = useAuth();
  const [status, setStatus] = useState<EngineStatusResponse | null>(null);
  const [refreshTick, setRefreshTick] = useState(0);

  const handleStatusChanged = useCallback((newStatus: EngineStatus) => {
    // Merge the partial update from the action response. We keep the
    // existing `lastTransitionAt` and `checkedAt` until the next poll
    // replaces them; this is fine for the Control Panel because it
    // only reads the status string, and the StatusCard will refetch
    // imminently thanks to the bump in refreshTick.
    setStatus((prev) =>
      prev
        ? { ...prev, status: newStatus }
        : prev
    );
    // Ask the StatusCard to refresh now so the badge updates
    // immediately (don't wait up to 3 s for the next poll).
    setRefreshTick((t) => t + 1);
  }, []);

  if (!auth) {
    // Should be unreachable — DashboardLayout is only rendered when auth is set.
    return null;
  }

  return (
    <div className="app-shell">
      <header className="header">
        <div className="header__brand">
          <div className="header__brand-mark" aria-hidden="true">BPL</div>
          <div className="header__brand-text">
            <span>Order Engine Admin</span>
            <span className="header__brand-sub">BPL Order Engine · Phase 1</span>
          </div>
        </div>
        <div className="header__right">
          <span className="card__subtitle" aria-label={`Signed in as ${auth.username}`}>
            {auth.username}
          </span>
          <RoleBadge role={auth.role} />
          <button type="button" className="btn btn--ghost" onClick={signOut}>
            Sign out
          </button>
        </div>
      </header>

      <main className="app-main">
        <div className="dashboard-grid">
          <StatusCard auth={auth} refreshTick={refreshTick} />
          <ControlPanel
            auth={auth}
            status={status?.status ?? null}
            onStatusChanged={handleStatusChanged}
          />
          <LogTerminal auth={auth} />
        </div>
      </main>
    </div>
  );
}
