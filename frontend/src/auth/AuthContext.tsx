import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { AuthContext, type AuthContextValue } from './AuthContextObject';
import { DEMO_USERS, type AuthState, type Role } from './types';

/**
 * Encode Basic Auth credentials. We use {@code btoa} (browser-builtin)
 * and fall back to a manual encoder for non-ASCII safety; passwords in
 * this app are ASCII but the fallback costs nothing.
 */
function encodeBasicAuth(username: string, password: string): string {
  const raw = `${username}:${password}`;
  if (typeof btoa === 'function') {
    try {
      return btoa(raw);
    } catch {
      // fall through to manual encoder if the input has non-Latin1 chars
    }
  }
  // Manual base64 (RFC 4648) for non-browser environments or non-Latin1 input.
  const utf8 = unescape(encodeURIComponent(raw));
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  let out = '';
  for (let i = 0; i < utf8.length; i += 3) {
    const a = utf8.charCodeAt(i);
    const b = i + 1 < utf8.length ? utf8.charCodeAt(i + 1) : NaN;
    const c = i + 2 < utf8.length ? utf8.charCodeAt(i + 2) : NaN;
    out += chars[a >> 2];
    out += chars[((a & 3) << 4) | (Number.isNaN(b) ? 0 : b >> 4)];
    out += Number.isNaN(b) ? '=' : chars[((b & 15) << 2) | (Number.isNaN(c) ? 0 : c >> 6)];
    out += Number.isNaN(c) ? '=' : chars[c & 63];
  }
  return out;
}

const STORAGE_KEY = 'bpl-admin-auth-v1';

/**
 * Read persisted credentials from {@code localStorage}. We keep the
 * encoded header so a page refresh in the middle of a demo doesn't
 * force the user to re-type the password.
 */
function readPersistedAuth(): AuthState | null {
  if (typeof localStorage === 'undefined') return null;
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as AuthState;
    if (
      typeof parsed.username === 'string' &&
      (parsed.role === 'ADMIN' || parsed.role === 'VIEWER') &&
      typeof parsed.authorizationHeader === 'string'
    ) {
      return parsed;
    }
  } catch {
    /* corrupt entry — drop it */
  }
  return null;
}

function writePersistedAuth(state: AuthState | null): void {
  if (typeof localStorage === 'undefined') return;
  if (state === null) {
    localStorage.removeItem(STORAGE_KEY);
  } else {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [auth, setAuth] = useState<AuthState | null>(() => readPersistedAuth());

  useEffect(() => {
    writePersistedAuth(auth);
  }, [auth]);

  const signIn = useCallback((username: string, password: string): boolean => {
    const trimmedUser = username.trim();
    if (!trimmedUser || !password) return false;
    const matchRole = (u: typeof DEMO_USERS.ADMIN | typeof DEMO_USERS.VIEWER) =>
      u.username === trimmedUser && u.password === password;
    let role: Role;
    if (matchRole(DEMO_USERS.ADMIN)) role = 'ADMIN';
    else if (matchRole(DEMO_USERS.VIEWER)) role = 'VIEWER';
    else return false;
    setAuth({
      username: trimmedUser,
      role,
      authorizationHeader: encodeBasicAuth(trimmedUser, password),
    });
    return true;
  }, []);

  const signInAs = useCallback((role: Role) => {
    const u = DEMO_USERS[role];
    setAuth({
      username: u.username,
      role: u.role,
      authorizationHeader: encodeBasicAuth(u.username, u.password),
    });
  }, []);

  const signOut = useCallback(() => {
    setAuth(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ auth, signIn, signInAs, signOut }),
    [auth, signIn, signInAs, signOut]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
