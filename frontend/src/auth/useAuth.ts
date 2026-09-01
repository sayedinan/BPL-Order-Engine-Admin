/**
 * useAuth — typed access to the AuthContext.
 *
 * Split out from AuthContext.tsx so the latter only exports the
 * provider component, satisfying the
 * `react-refresh/only-export-components` lint rule (Fast Refresh
 * needs component-only files to preserve local state on edit).
 */
import { useContext } from 'react';
import { AuthContext, type AuthContextValue } from './context';

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an <AuthProvider>');
  }
  return ctx;
}
