import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';

export default function AdminLayout() {
  const { user, logout } = useAuth();
  const nav = useNavigate();

  async function onSignOut() {
    await logout();
    nav('/login', { replace: true });
  }

  return (
    <>
      <header className="topbar">
        <div className="nav">
          <NavLink to="/admin/engines" className={({ isActive }) => (isActive ? 'active' : '')}>
            Engines
          </NavLink>
        </div>
        <div className="user">
          {user && (
            <>
              <span>{user.username}</span>
              <span style={{ color: '#999' }}>({user.role})</span>
              <button className="linkish" onClick={onSignOut} data-testid="signout">
                Sign out
              </button>
            </>
          )}
        </div>
      </header>
      <main className="page">
        <Outlet />
      </main>
    </>
  );
}
