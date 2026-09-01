import { useContext } from 'react';
import { AuthContext, type AuthContextValue } from './AuthContextObject';

/**
 * Hook used by every component that needs to read the current auth
 * state. Kept in its own file (not alongside {@code AuthProvider}) so
 * the {@code react-refresh/only-export-components} lint rule is
 * satisfied &mdash; Fast Refresh needs component-only files to
 * preserve local state on edit.
 */
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an <AuthProvider>');
  }
  return ctx;
}
