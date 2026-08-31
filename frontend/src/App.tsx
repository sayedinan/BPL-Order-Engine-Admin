import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './auth/useAuth';
import LoginPage from './auth/LoginPage';
import AdminLayout from './admin/AdminLayout';
import EngineListPage from './admin/EngineListPage';
import EngineAddPage from './admin/EngineAddPage';

/**
 * Top-level route table. Redirects to /login if unauthenticated
 * and the user navigates to a protected route.
 */
export default function App() {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return <div className="page">Loading…</div>;
  }

  if (!user && location.pathname !== '/login') {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/admin" element={<AdminLayout />}>
        <Route index element={<Navigate to="engines" replace />} />
        <Route path="engines" element={<EngineListPage />} />
        <Route path="engines/new" element={<EngineAddPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/admin/engines" replace />} />
    </Routes>
  );
}
