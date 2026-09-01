/**
 * AppShell — the authenticated chrome.
 *
 * Top bar: brand mark, nav (Dashboard / Logs / Admin — Admin hidden for
 * USER), role badge, logout button. Renders an <Outlet/> for the page.
 *
 * The Admin link is rendered conditionally on the user role. The
 * /admin route itself is lazy-imported in App.tsx — the link is just
 * the nav UX, the security boundary is the route definition.
 */
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { RoleBadge } from './RoleBadge';

export function AppShell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  if (!user) return null;

  async function handleSignOut() {
    await logout();
    navigate('/login', { replace: true });
  }

  const isAdmin = user.role === 'ADMIN' || user.role === 'SYS_ADMIN';

  return (
    <div className="app-shell">
      <header className="header">
        <div className="header__brand">
          <div className="header__brand-mark" aria-hidden="true">BPL</div>
          <div className="header__brand-text">
            <span>Order Engine Admin</span>
            <span className="header__brand-sub">BPL Order Engine · v0.3</span>
          </div>
        </div>

        <nav className="header__nav" aria-label="Primary navigation">
          <NavLink
            to="/dashboard"
            className={({ isActive }) =>
              `header__nav-link${isActive ? ' header__nav-link--active' : ''}`
            }
          >
            Dashboard
          </NavLink>
          <NavLink
            to="/logs"
            className={({ isActive }) =>
              `header__nav-link${isActive ? ' header__nav-link--active' : ''}`
            }
          >
            Logs
          </NavLink>
          {isAdmin && (
            <NavLink
              to="/admin"
              className={({ isActive }) =>
                `header__nav-link${isActive ? ' header__nav-link--active' : ''}`
              }
            >
              Admin
            </NavLink>
          )}
        </nav>

        <div className="header__right">
          <span className="card__subtitle" aria-label={`Signed in as ${user.username}`}>
            {user.username}
          </span>
          <RoleBadge role={user.role} />
          <button
            type="button"
            className="btn btn--ghost"
            onClick={handleSignOut}
          >
            Sign out
          </button>
        </div>
      </header>

      <main className="app-main">
        <Outlet />
      </main>
    </div>
  );
}

/** Re-export the role badge so callers don't need a separate import. */
export { RoleBadge };
