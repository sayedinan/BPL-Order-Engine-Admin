/**
 * AuthContext value type + the React context object.
 *
 * Split out from AuthContext.tsx so the latter only exports the
 * provider component, satisfying the
 * `react-refresh/only-export-components` lint rule.
 */
import { createContext } from 'react';
import type { UserResponse } from '../api/types';

export interface AuthContextValue {
  user: UserResponse | null;
  token: string | null;
  mustChangePassword: boolean;
  isLoading: boolean;
  login: (username: string, password: string) => Promise<UserResponse>;
  logout: () => Promise<void>;
  changePassword: (
    currentPassword: string,
    newPassword: string,
  ) => Promise<UserResponse>;
  refresh: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
