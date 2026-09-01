/**
 * Admin panel — Users and Engines tabs.
 *
 * Users tab (SYS_ADMIN full; ADMIN USER-only): table with username,
 * role, assigned engines, [Edit] [Delete]. "+ Add User" opens the
 * UserForm modal. The role select inside the modal is constrained by
 * the caller's role per SPEC §3.1.
 *
 * Engines tab (SYS_ADMIN only; ADMIN read-only): table with name,
 * code, mode, serverIp, [Edit SSH] [Delete]. "+ Add Engine" opens
 * the EngineForm modal.
 *
 * This module is lazy-imported in App.tsx so it's not in the USER
 * bundle. The component itself also re-checks the role and would
 * redirect to /403 if it ever rendered for a USER (defense layer).
 */
import { useCallback, useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { enginesApi, usersApi } from '../api/client';
import type {
  CreateEngineRequest,
  CreateUserRequest,
  EngineResponse,
  Role,
  UserResponse,
} from '../api/types';
import { EngineForm } from '../components/EngineForm';
import { UserForm } from '../components/UserForm';

type Tab = 'users' | 'engines';

export function Admin() {
  const { user } = useAuth();
  // Defense layer: even though App.tsx routes /admin away from USER,
  // a stale render after a role change could land here. Bounce.
  if (!user || user.role === 'USER') {
    return <Navigate to="/403" replace />;
  }
  return <AdminPanel user={user} />;
}

function AdminPanel({ user }: { user: NonNullable<ReturnType<typeof useAuth>['user']> }) {
  const isSysAdmin = user.role === 'SYS_ADMIN';
  const [tab, setTab] = useState<Tab>('users');
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [engines, setEngines] = useState<EngineResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [reloadTick, setReloadTick] = useState(0);

  const reload = useCallback(() => setReloadTick((t) => t + 1), []);

  // Fetch users (SYS_ADMIN + ADMIN).
  useEffect(() => {
    let cancelled = false;
    usersApi
      .list()
      .then((list) => {
        if (cancelled) return;
        setUsers(list);
        setError(null);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(err instanceof Error ? err.message : 'Failed to load users');
      });
    return () => {
      cancelled = true;
    };
  }, [reloadTick]);

  // Fetch engines (SYS_ADMIN + ADMIN; ADMIN sees the same list as
  // Dashboard, but read-only here).
  useEffect(() => {
    let cancelled = false;
    enginesApi
      .list()
      .then((list) => {
        if (cancelled) return;
        setEngines(list);
      })
      .catch(() => {
        /* surface in user/engine lists; not fatal */
      });
    return () => {
      cancelled = true;
    };
  }, [reloadTick]);

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Admin</h1>
          <div className="page-header__subtitle">
            Manage users and engines.
          </div>
        </div>
      </div>

      <div className="tabs" role="tablist">
        <button
          type="button"
          className={`tabs__tab${tab === 'users' ? ' tabs__tab--active' : ''}`}
          onClick={() => setTab('users')}
          role="tab"
          aria-selected={tab === 'users'}
        >
          Users ({users.length})
        </button>
        <button
          type="button"
          className={`tabs__tab${tab === 'engines' ? ' tabs__tab--active' : ''}`}
          onClick={() => setTab('engines')}
          role="tab"
          aria-selected={tab === 'engines'}
        >
          Engines ({engines.length})
        </button>
      </div>

      {error && (
        <div className="control-panel__error" role="alert">
          {error}
        </div>
      )}

      {tab === 'users' && (
        <UsersTab
          users={users}
          engines={engines}
          currentUserId={user?.id ?? ''}
          isSysAdmin={isSysAdmin}
          reload={reload}
        />
      )}
      {tab === 'engines' && (
        <EnginesTab
          engines={engines}
          isSysAdmin={isSysAdmin}
          reload={reload}
        />
      )}
    </>
  );
}

// ---- Users tab ----

interface UsersTabProps {
  users: UserResponse[];
  engines: EngineResponse[];
  currentUserId: string;
  isSysAdmin: boolean;
  reload: () => void;
}

function UsersTab({ users, engines, currentUserId, isSysAdmin, reload }: UsersTabProps) {
  const [editing, setEditing] = useState<UserResponse | null>(null);
  const [adding, setAdding] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  async function handleCreate(body: CreateUserRequest | unknown) {
    setActionError(null);
    try {
      await usersApi.create(body as CreateUserRequest);
      setAdding(false);
      reload();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Failed to create user');
    }
  }

  async function handleUpdate(
    body: CreateUserRequest | { role?: Role; assignedEngineCodes?: string[] },
  ) {
    if (!editing) return;
    setActionError(null);
    try {
      await usersApi.updateRoles(editing.id, body as { role?: Role; assignedEngineCodes?: string[] });
      setEditing(null);
      reload();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Failed to update user');
    }
  }

  async function handleDelete(target: UserResponse) {
    if (target.id === currentUserId) {
      setActionError('You cannot delete yourself.');
      return;
    }
    if (!window.confirm(`Delete user "${target.username}"? This cannot be undone.`)) {
      return;
    }
    setBusyId(target.id);
    setActionError(null);
    try {
      await usersApi.remove(target.id);
      reload();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Failed to delete user');
    } finally {
      setBusyId(null);
    }
  }

  // ADMIN can only act on USER-role rows. SYS_ADMIN can act on anyone.
  function canEditRow(row: UserResponse): boolean {
    if (row.id === currentUserId) return false;
    if (isSysAdmin) return true;
    return row.role === 'USER';
  }
  function canDeleteRow(row: UserResponse): boolean {
    if (row.id === currentUserId) return false;
    if (isSysAdmin) return true;
    return row.role === 'USER';
  }

  return (
    <>
      <div
        className="filter-bar"
        style={{ justifyContent: 'flex-end', marginBottom: 12 }}
      >
        <button
          type="button"
          className="btn btn--primary"
          onClick={() => setAdding(true)}
        >
          + Add User
        </button>
      </div>

      {actionError && (
        <div className="control-panel__error" role="alert" style={{ marginBottom: 12 }}>
          {actionError}
        </div>
      )}

      <table className="data-table">
        <thead>
          <tr>
            <th>Username</th>
            <th>Role</th>
            <th>Assigned engines</th>
            <th style={{ width: 160 }}>Actions</th>
          </tr>
        </thead>
        <tbody>
          {users.length === 0 ? (
            <tr>
              <td colSpan={4}>
                <div className="empty-state" style={{ margin: 0, border: 'none' }}>
                  No users.
                </div>
              </td>
            </tr>
          ) : (
            users.map((u) => (
              <tr key={u.id}>
                <td>
                  <strong>{u.username}</strong>
                  {u.id === currentUserId && (
                    <span className="form__hint" style={{ marginLeft: 6 }}>
                      (you)
                    </span>
                  )}
                </td>
                <td className="data-table__code">{u.role}</td>
                <td className="data-table__code">
                  {u.assignedEngineCodes.length === 0
                    ? '—'
                    : u.assignedEngineCodes.join(', ')}
                </td>
                <td>
                  <div className="data-table__actions">
                    <button
                      type="button"
                      className="btn"
                      onClick={() => setEditing(u)}
                      disabled={!canEditRow(u) || busyId === u.id}
                    >
                      Edit
                    </button>
                    <button
                      type="button"
                      className="btn btn--danger"
                      onClick={() => handleDelete(u)}
                      disabled={!canDeleteRow(u) || busyId === u.id}
                    >
                      {busyId === u.id ? 'Deleting…' : 'Delete'}
                    </button>
                  </div>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      {adding && (
        <UserForm
          mode="create"
          availableEngines={engines}
          onCancel={() => setAdding(false)}
          onSubmit={handleCreate}
        />
      )}
      {editing && (
        <UserForm
          mode="edit"
          initial={editing}
          availableEngines={engines}
          onCancel={() => setEditing(null)}
          onSubmit={handleUpdate}
        />
      )}
    </>
  );
}

// ---- Engines tab ----

interface EnginesTabProps {
  engines: EngineResponse[];
  isSysAdmin: boolean;
  reload: () => void;
}

function EnginesTab({ engines, isSysAdmin, reload }: EnginesTabProps) {
  const [editing, setEditing] = useState<EngineResponse | null>(null);
  const [adding, setAdding] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [busyCode, setBusyCode] = useState<string | null>(null);

  async function handleCreate(body: CreateEngineRequest | unknown) {
    setActionError(null);
    try {
      await enginesApi.create(body as CreateEngineRequest);
      setAdding(false);
      reload();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Failed to create engine');
    }
  }

  async function handleUpdate(body: CreateEngineRequest | unknown) {
    if (!editing) return;
    setActionError(null);
    try {
      await enginesApi.updateSsh(editing.code, body as Parameters<typeof enginesApi.updateSsh>[1]);
      setEditing(null);
      reload();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Failed to update engine');
    }
  }

  async function handleDelete(target: EngineResponse) {
    if (!window.confirm(`Delete engine "${target.code}"? This is a soft-delete; the row is hidden from the factory.`)) {
      return;
    }
    setBusyCode(target.code);
    setActionError(null);
    try {
      await enginesApi.remove(target.code);
      reload();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Failed to delete engine');
    } finally {
      setBusyCode(null);
    }
  }

  return (
    <>
      <div
        className="filter-bar"
        style={{ justifyContent: 'flex-end', marginBottom: 12 }}
      >
        {isSysAdmin && (
          <button
            type="button"
            className="btn btn--primary"
            onClick={() => setAdding(true)}
          >
            + Add Engine
          </button>
        )}
      </div>

      {actionError && (
        <div className="control-panel__error" role="alert" style={{ marginBottom: 12 }}>
          {actionError}
        </div>
      )}

      <table className="data-table">
        <thead>
          <tr>
            <th>Name</th>
            <th>Code</th>
            <th>Mode</th>
            <th>Server</th>
            <th style={{ width: 180 }}>Actions</th>
          </tr>
        </thead>
        <tbody>
          {engines.length === 0 ? (
            <tr>
              <td colSpan={5}>
                <div className="empty-state" style={{ margin: 0, border: 'none' }}>
                  No engines.
                </div>
              </td>
            </tr>
          ) : (
            engines.map((e) => (
              <tr key={e.code}>
                <td>
                  <strong>{e.name}</strong>
                </td>
                <td className="data-table__code">{e.code}</td>
                <td className="data-table__code">{e.mode}</td>
                <td className="data-table__code">
                  {e.serverUsername}@{e.serverIp}
                </td>
                <td>
                  {isSysAdmin ? (
                    <div className="data-table__actions">
                      <button
                        type="button"
                        className="btn"
                        onClick={() => setEditing(e)}
                        disabled={busyCode === e.code}
                      >
                        Edit SSH
                      </button>
                      <button
                        type="button"
                        className="btn btn--danger"
                        onClick={() => handleDelete(e)}
                        disabled={busyCode === e.code}
                      >
                        {busyCode === e.code ? 'Deleting…' : 'Delete'}
                      </button>
                    </div>
                  ) : (
                    <span className="form__hint">Read-only</span>
                  )}
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      {adding && (
        <EngineForm
          mode="create"
          onCancel={() => setAdding(false)}
          onSubmit={handleCreate}
        />
      )}
      {editing && (
        <EngineForm
          mode="edit"
          initial={editing}
          onCancel={() => setEditing(null)}
          onSubmit={handleUpdate}
        />
      )}
    </>
  );
}
