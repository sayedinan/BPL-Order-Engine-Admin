import { AuthProvider } from './auth/AuthContext';
import { useAuth } from './auth/useAuth';
import { LoginScreen } from './auth/LoginScreen';
import { DashboardLayout } from './dashboard/DashboardLayout';
import './index.css';
import './dashboard.css';

/**
 * Top-level router. With no client-side router in this phase, we
 * branch on the presence of an auth state. Both branches render full
 * screen so there's no layout shift on sign-in / sign-out.
 */
function AppShell() {
  const { auth } = useAuth();
  return auth ? <DashboardLayout /> : <LoginScreen />;
}

export default function App() {
  return (
    <AuthProvider>
      <AppShell />
    </AuthProvider>
  );
}
