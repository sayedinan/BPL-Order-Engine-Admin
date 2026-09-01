/**
 * App.tsx — the router root.
 *
 * Mounts the AuthProvider, then the BrowserRouter with 7 routes:
 *   /login             (public)
 *   /dashboard         (authenticated)
 *   /logs              (authenticated)
 *   /admin             (authenticated, lazy for non-USER)
 *   /change-password   (authenticated, while mustChangePassword=true)
 *   /404, /403         (always)
 *
 * Two route guards:
 *   - RequireAuth: bounces unauthenticated users to /login.
 *   - MustChangePasswordGuard: bounces users with mustChangePassword=true
 *     who try to reach /dashboard, /logs, or /admin back to
 *     /change-password. Defense in depth (the server enforces too).
 */
import { lazy, Suspense, useEffect, type ReactNode } from 'react';
import {
  BrowserRouter,
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { useAuth } from './auth/useAuth';
import { setOnUnauthorized } from './api/client';
import { AppShell } from './components/AppShell';
import { Login } from './pages/Login';
import { ChangePassword } from './pages/ChangePassword';
import { NotFound } from './pages/NotFound';
import { Forbidden } from './pages/Forbidden';
import { Dashboard } from './pages/Dashboard';
import { Logs } from './pages/Logs';

// Lazy import for the Admin page — keeps it out of the USER bundle.
const Admin = lazy(() =>
  import('./pages/Admin').then((m) => ({ default: m.Admin })),
);

function FullPageLoader() {
  return (
    <div className="app-main">
      <div className="card__subtitle">Loading…</div>
    </div>
  );
}

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, isLoading } = useAuth();
  const location = useLocation();
  if (isLoading) return <FullPageLoader />;
  if (!user) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: location.pathname + location.search }}
      />
    );
  }
  return <>{children}</>;
}

function MustChangePasswordGuard({ children }: { children: ReactNode }) {
  const { user, mustChangePassword } = useAuth();
  if (user && mustChangePassword) {
    return <Navigate to="/change-password" replace />;
  }
  return <>{children}</>;
}

function AdminGuard({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (user.role === 'USER') return <Navigate to="/403" replace />;
  return <>{children}</>;
}

/**
 * Bridges the api/client's 401 handler to the React Router so a 401
 * during a fetch call navigates to /login instead of doing a full
 * page reload. Without this, the api/client would do
 * `window.location.href = '/login'` which is a full reload and loses
 * state.
 */
function UnauthorizedBridge() {
  const navigate = useNavigate();
  const location = useLocation();
  useEffect(() => {
    setOnUnauthorized(() => {
      // Only navigate if we're not already on the login page.
      if (location.pathname !== '/login') {
        navigate('/login', { replace: true });
      }
    });
    return () => setOnUnauthorized(null);
  }, [navigate, location.pathname]);
  return null;
}

function LoadingScreen() {
  const { isLoading } = useAuth();
  if (!isLoading) return null;
  return <FullPageLoader />;
}

function AppRoutes() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <LoadingScreen />
        <UnauthorizedBridge />
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/change-password" element={<ChangePassword />} />

          <Route
            element={
              <RequireAuth>
                <MustChangePasswordGuard>
                  <AppShell />
                </MustChangePasswordGuard>
              </RequireAuth>
            }
          >
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/logs" element={<Logs />} />
            <Route
              path="/admin/*"
              element={
                <AdminGuard>
                  <Suspense fallback={<FullPageLoader />}>
                    <Admin />
                  </Suspense>
                </AdminGuard>
              }
            />
          </Route>

          <Route path="/403" element={<Forbidden />} />
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default function App() {
  return <AppRoutes />;
}
