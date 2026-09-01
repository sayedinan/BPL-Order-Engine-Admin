import { createContext } from 'react';
import type { AuthState, Role } from './types';

/**
 * The auth context object, split out from {@code AuthContext.tsx} so
 * that file can only export the {@code AuthProvider} component and
 * satisfy the {@code react-refresh/only-export-components} rule.
 */
export interface AuthContextValue {
  auth: AuthState | null;
  /** Sign in with explicit username/password. Returns true on success. */
  signIn(username: string, password: string): boolean;
  /** Sign in with one of the pre-seeded demo users. */
  signInAs(role: Role): void;
  /** Drop credentials. The user is returned to the Login screen. */
  signOut(): void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
