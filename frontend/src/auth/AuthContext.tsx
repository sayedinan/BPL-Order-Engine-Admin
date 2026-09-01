/**
 * The v0.3 AuthContext.
 *
 * Source of truth for "who is logged in" in the React app. Exposes
 * { user, token, mustChangePassword, isLoading, login, logout,
 * changePassword, refresh } per the auth-context-pattern skill.
 *
 * The token lives in `localStorage` under `bpl-admin.token`. The
 * api/client reads it on every request and clears it on 401, which
 * triggers this provider's `setUser(null)` on the next render.
 *
 * The Login page is responsible for the post-login redirect (to
 * /change-password if mustChangePassword, else /dashboard). The
 * AppShell handles the mustChangePassword route guard for an existing
 * user with a stale flag.
 */
import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { authApi, getToken, setToken } from '../api/client';
import type { UserResponse } from '../api/types';
import { AuthContext, type AuthContextValue } from './context';

export type { AuthContextValue } from './context';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [token, setTokenState] = useState<string | null>(() => getToken());
  const [isLoading, setIsLoading] = useState(true);

  // On mount (and when the token changes externally — e.g. from a
  // successful login in another tab via the `storage` event), validate
  // the token with /me. A 401 means the token is bad; clear and the
  // route guard redirects to /login.
  useEffect(() => {
    let cancelled = false;
    async function probe() {
      if (!token) {
        setUser(null);
        setIsLoading(false);
        return;
      }
      try {
        const me = await authApi.me();
        if (!cancelled) {
          setUser(me);
          setIsLoading(false);
        }
      } catch {
        if (!cancelled) {
          setToken(null);
          setTokenState(null);
          setUser(null);
          setIsLoading(false);
        }
      }
    }
    probe();
    return () => {
      cancelled = true;
    };
  }, [token]);

  const login = useCallback(
    async (username: string, password: string): Promise<UserResponse> => {
      // The mock accepts `<username>123`. The real backend takes the
      // user's literal input.
      const res = await authApi.login({ username, password });
      setToken(res.token);
      setTokenState(res.token);
      setUser(res.user);
      return res.user;
    },
    [],
  );

  const logout = useCallback(async () => {
    try {
      // Best-effort — the server writes a LOGOUT audit row.
      await authApi.logout();
    } catch {
      /* even if the network call fails, clear the local state */
    }
    setToken(null);
    setTokenState(null);
    setUser(null);
  }, []);

  const changePassword = useCallback(
    async (
      currentPassword: string,
      newPassword: string,
    ): Promise<UserResponse> => {
      const res = await authApi.changePassword({ currentPassword, newPassword });
      setToken(res.token);
      setTokenState(res.token);
      setUser(res.user);
      return res.user;
    },
    [],
  );

  const refresh = useCallback(async () => {
    const me = await authApi.me();
    setUser(me);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      token,
      mustChangePassword: user?.mustChangePassword ?? false,
      isLoading,
      login,
      logout,
      changePassword,
      refresh,
    }),
    [user, token, isLoading, login, logout, changePassword, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
