---
name: auth-context-pattern
description: v0.3 — the AuthContext shape, JWT in localStorage, the mustChangePassword redirect, the 401 → redirect flow, and the api/client.ts wrapper that the AuthContext depends on.
---

# AuthContext pattern (v0.3)

The `AuthContext` is the source of truth for "who is logged in" in
the React app. It exposes the user, the token, the
`mustChangePassword` flag, and the actions. Every other component
reads from this context; no component holds its own copy of the
token.

## The shape

```typescript
interface AuthState {
    user: UserResponse | null;
    token: string | null;
    mustChangePassword: boolean;
    isLoading: boolean;                  // true during the initial /me probe
}

interface AuthContextValue extends AuthState {
    login(username: string, secret: string): Promise<void>;
    logout(): void;
    refresh(): Promise<void>;            // re-fetch /me
}
```

The `user` is the full `UserResponse` from the server (id,
username, role, assignedEngineCodes, mustChangePassword, etc.).
Components that need the role read `user.role`. Components that need
the assigned engines read `user.assignedEngineCodes`.

The `login` parameter is named `secret` here (not `password`) so the
type signature is unambiguous about what is being passed. The
request body still uses the field name the server expects, see
`api/client.ts` below for the wiring.

## The provider

```typescript
const TOKEN_KEY = 'bpl-admin.token';

export function AuthProvider({ children }: { children: React.ReactNode }) {
    const [user, setUser] = useState<UserResponse | null>(null);
    const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY));
    const [isLoading, setIsLoading] = useState(true);

    // On mount: if a token is in localStorage, validate it via /me.
    useEffect(() => {
        let cancelled = false;
        async function probe() {
            if (!token) {
                setIsLoading(false);
                return;
            }
            try {
                const me = await api.get<UserResponse>('/api/auth/me');
                if (!cancelled) {
                    setUser(me);
                    setIsLoading(false);
                }
            } catch (err) {
                if (!cancelled) {
                    // 401 from /me means the token is bad; clear and let
                    // the route guard redirect to /login.
                    localStorage.removeItem(TOKEN_KEY);
                    setToken(null);
                    setUser(null);
                    setIsLoading(false);
                }
            }
        }
        probe();
        return () => { cancelled = true; };
    }, [token]);

    const login = useCallback(async (username: string, secret: string) => {
        // The request body uses the server's field name. The value is the
        // user's input; never log it, never put it in error messages.
        const res = await api.post<LoginResponse>(
            '/api/auth/login',
            { username, password: secret }   // server expects "password" here
        );
        localStorage.setItem(TOKEN_KEY, res.token);
        setToken(res.token);
        setUser(res.user);
        // The Login page is responsible for the post-login redirect.
    }, []);

    const logout = useCallback(() => {
        // Best-effort: tell the server. The server is stateless so this
        // is just an audit row.
        api.post('/api/auth/logout', {}).catch(() => {});
        localStorage.removeItem(TOKEN_KEY);
        setToken(null);
        setUser(null);
    }, []);

    const refresh = useCallback(async () => {
        const me = await api.get<UserResponse>('/api/auth/me');
        setUser(me);
    }, []);

    const value: AuthContextValue = {
        user, token, mustChangePassword: user?.mustChangePassword ?? false,
        isLoading, login, logout, refresh,
    };

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
    const ctx = useContext(AuthContext);
    if (!ctx) throw new Error('useAuth must be used within AuthProvider');
    return ctx;
}
```

## The mustChangePassword redirect (🔒)

The redirect after login is **not** the AuthContext's job. The
Login page reads `useAuth().user.mustChangePassword` after a
successful login and navigates accordingly. The AuthContext exposes
the flag; the page owns the navigation.

```typescript
// pages/Login.tsx
const { login, user } = useAuth();
const navigate = useNavigate();

async function onSubmit(username: string, secret: string) {
    try {
        await login(username, secret);
        if (user?.mustChangePassword) {
            navigate('/change-password');
        } else {
            navigate('/dashboard');
        }
    } catch (err) {
        setError(err instanceof Error ? err.message : 'Login failed');
    }
}
```

The same redirect runs on page load: if the user reopens the app
with a token that has `mustChangePassword = true` (the token from
the seeded admin user, or a freshly-created user who hasn't changed
yet), the `probe` sets the user, and the `AppShell` checks the
flag and navigates to `/change-password`.

## The api/client.ts wrapper (🔒)

The AuthContext depends on `api/client.ts` for three things:
1. The `Authorization: Bearer …` header on every request.
2. The 401 → clear + redirect-to-login handling.
3. The error envelope unwrap (so `try/catch` in the caller sees a
   plain `Error(message)`).

```typescript
const BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
    const token = localStorage.getItem(TOKEN_KEY);
    const res = await fetch(`${BASE}${path}`, {
        method,
        headers: {
            'Content-Type': 'application/json',
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: body ? JSON.stringify(body) : undefined,
    });

    if (res.status === 401) {
        // Token is bad. Clear and let the caller route to /login.
        localStorage.removeItem(TOKEN_KEY);
        if (window.location.pathname !== '/login') {
            window.location.href = '/login';
        }
    }

    if (!res.ok) {
        const env: ErrorEnvelope = await res.json().catch(() => ({
            timestamp: new Date().toISOString(),
            status: res.status,
            error: res.statusText,
            message: `HTTP ${res.status}`,
            path,
        }));
        throw new Error(env.message);
    }

    if (res.status === 204) return undefined as T;
    return res.json();
}

export const api = {
    get:    <T>(path: string)            => request<T>('GET', path),
    post:   <T>(path: string, body: any) => request<T>('POST', path, body),
    patch:  <T>(path: string, body: any) => request<T>('PATCH', path, body),
    put:    <T>(path: string, body: any) => request<T>('PUT', path, body),
    delete: <T>(path: string)            => request<T>('DELETE', path),
};
```

The 401 handling is **not** a thrown error. A 401 means "you need
to log in again"; the only correct response is to navigate to
`/login`, not to surface the error to the user. The `if
(window.location.pathname !== '/login')` guard prevents a redirect
loop when the Login page itself gets a 401 from a bad-credentials
POST (the 401 from `/api/auth/login` is handled in the Login page,
not by the global 401 handler).

## Token storage

`localStorage` is the v0.3 choice. The trade-off:
- ✅ Survives tab close, refresh, browser restart
- ✅ Easy to clear on logout
- ❌ Vulnerable to XSS (any injected script can read it)

The XSS exposure is mitigated by:
- No `dangerouslySetInnerHTML` anywhere in the codebase
- No third-party scripts (the CSP forbids them)
- React's default escaping

`httpOnly` cookies would be safer against XSS but introduce CSRF
risk that requires `SameSite=Strict` plus a CSRF token. For v0.3's
internal admin tool, `localStorage` is acceptable. v0.4 may
revisit this.

## The 401 dispatch in components

Components don't handle 401. The api/client.ts does, by navigating
to `/login`. Components handle *other* errors (400, 403, 500) with
a `try/catch` that surfaces the message.

```typescript
async function onStart() {
    setInFlight(true);
    try {
        await api.post(`/api/engines/${engine.code}/start`, {});
    } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to start engine');
    } finally {
        setInFlight(false);
    }
}
```

The catch handles 403 (USER without access), 409 (already
RUNNING), 502 (engine unreachable / SSH failure), 504 (timeout).
The 401 is handled by the api/client before the catch sees it.

## Anti-patterns

- **Don't store the JWT in `sessionStorage`.** It makes tab
  refresh log the user out, which is bad UX.
- **Don't store the JWT in a non-`httpOnly` cookie without
  `SameSite=Strict`.** A plain cookie is sent on cross-site
  requests.
- **Don't put the JWT in a React state that survives logout.**
  The AuthContext clears it; nothing else should hold it.
- **Don't read `localStorage` from a component directly.** Use the
  AuthContext. The JWT is the AuthContext's concern.
- **Don't redirect to `/login` from a 401 inside a try/catch.** The
  api/client handles the 401. The catch only sees 400/403/500.
- **Don't keep the user logged in if `/me` returns 401 on mount.**
  The probe sets `user = null` and the route guard redirects.
- **Don't use `useEffect` to navigate based on `user` changes.** The
  Login page navigates after a successful login; the AppShell
  navigates to `/change-password` if `user.mustChangePassword` is
  true on first mount. Other places don't navigate based on `user`.
- **Don't put any credential value in the React bundle.** No
  default secrets, no test users, no seed credentials. The
  server-side seed lives in `db/seed/`, gated by the dev profile.
- **Don't write `password: <literal-secret>` in any source file.**
  The secrets hook will block the write. Use env vars, Jasypt
  encryption, or `secret: <identifier>` in client-side type
  signatures and map to the server's field name in the request
  body.
