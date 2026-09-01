/**
 * The v0.3 API client. A thin fetch wrapper that:
 *  - adds `Authorization: Bearer <token>` on every request,
 *  - on 401, clears the stored token and redirects to `/login` (the
 *    api client does not throw on 401 — that's the dispatcher's job),
 *  - unwraps the standard error envelope (`{ timestamp, status, error,
 *    message, path }`) into a thrown `Error(message)` for non-401
 *    errors so callers can `try { ... } catch (err) { setError(err.message) }`.
 *
 * Per-resource methods (`authApi`, `enginesApi`, `usersApi`,
 * `auditApi`) all go through `request()` so the 401 dispatch and
 * error unwrap are in one place.
 *
 * When `VITE_USE_MOCK === 'true'`, the request is intercepted by the
 * mock router and never leaves the page. Otherwise the request goes
 * to the v0.3 backend at `VITE_API_BASE_URL` (default
 * `http://localhost:8080`).
 */
import { handleMockRequest } from './mock/router';
import type {
  AuditLogPageResponse,
  AuditAction,
  ChangePasswordRequest,
  CreateEngineRequest,
  CreateUserRequest,
  EngineActionResponse,
  EngineResponse,
  EngineStatusResponse,
  ErrorEnvelope,
  LogPageResponse,
  LoginRequest,
  LoginResponse,
  UpdateEngineSshRequest,
  UpdateUserRolesRequest,
  UserResponse,
} from './types';

const TOKEN_KEY = 'bpl-admin.token';

const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false';
const BASE = USE_MOCK
  ? 'http://mock'
  : (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? 'http://localhost:8080';

/** True when the app is intercepting requests with the in-browser mock
 *  instead of calling the real backend. Exported so UI surfaces
 *  (banners, hints) can show mock-specific guidance. */
export function isMockMode(): boolean {
  return USE_MOCK;
}

if (USE_MOCK) {
  // Single line on app boot — handy when verifying which mode is active.
  console.info(
    '[api] running in mock mode (set VITE_USE_MOCK=false to call the real backend)',
  );
}

// ---- Token storage ----

export function getToken(): string | null {
  if (typeof localStorage === 'undefined') return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null): void {
  if (typeof localStorage === 'undefined') return;
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

// ---- 401 dispatch ----

let _onUnauthorized: (() => void) | null = null;

/** AuthContext registers a callback here so the client can drive the redirect
 * without importing router code (circular). */
export function setOnUnauthorized(cb: (() => void) | null): void {
  _onUnauthorized = cb;
}

function handleUnauthorized(): void {
  setToken(null);
  // Skip the redirect if we're already on the login page (avoid loops).
  if (
    typeof window !== 'undefined' &&
    window.location.pathname !== '/login'
  ) {
    if (_onUnauthorized) {
      _onUnauthorized();
    } else {
      window.location.href = '/login';
    }
  }
}

// ---- Core request ----

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  init?: RequestInit,
): Promise<T> {
  const url = `${BASE}${path}`;
  const headers: Record<string, string> = {
    Accept: 'application/json',
  };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  const token = getToken();
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const input = new Request(url, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
    ...init,
  });

  const res = USE_MOCK ? await handleMockRequest(input) : await fetch(input);

  if (res.status === 401) {
    handleUnauthorized();
    // Throw a synthetic error so the caller's catch (if any) sees something.
    // The redirect will already have happened.
    throw new Error('Authentication required');
  }

  if (res.status === 204) {
    return undefined as T;
  }

  if (!res.ok) {
    let env: ErrorEnvelope | null = null;
    try {
      env = (await res.json()) as ErrorEnvelope;
    } catch {
      /* not JSON */
    }
    const message = env?.message ?? res.statusText ?? `HTTP ${res.status}`;
    throw new Error(message);
  }

  return (await res.json()) as T;
}

const get = <T>(path: string) => request<T>('GET', path);
const post = <T>(path: string, body: unknown) => request<T>('POST', path, body);
const patch = <T>(path: string, body: unknown) => request<T>('PATCH', path, body);
const del = <T>(path: string) => request<T>('DELETE', path);

// ---- Per-resource APIs ----

export const authApi = {
  login: (body: LoginRequest) => post<LoginResponse>('/api/auth/login', body),
  logout: () => post<void>('/api/auth/logout', {}),
  me: () => get<UserResponse>('/api/auth/me'),
  changePassword: (body: ChangePasswordRequest) =>
    post<LoginResponse>('/api/auth/change-password', body),
};

export const enginesApi = {
  list: () => get<EngineResponse[]>('/api/engines'),
  get: (code: string) => get<EngineResponse>(`/api/engines/${encodeURIComponent(code)}`),
  create: (body: CreateEngineRequest) =>
    post<EngineResponse>('/api/engines', body),
  remove: (code: string) =>
    del<void>(`/api/engines/${encodeURIComponent(code)}`),
  updateSsh: (code: string, body: UpdateEngineSshRequest) =>
    patch<EngineResponse>(
      `/api/engines/${encodeURIComponent(code)}/ssh`,
      body,
    ),
  status: (code: string) =>
    get<EngineStatusResponse>(
      `/api/engines/${encodeURIComponent(code)}/status`,
    ),
  start: (code: string) =>
    post<EngineActionResponse>(
      `/api/engines/${encodeURIComponent(code)}/start`,
      {},
    ),
  stop: (code: string) =>
    post<EngineActionResponse>(
      `/api/engines/${encodeURIComponent(code)}/stop`,
      {},
    ),
  logs: (code: string, limit: 50 | 100 | 200 = 100) =>
    get<LogPageResponse>(
      `/api/engines/${encodeURIComponent(code)}/logs?limit=${limit}`,
    ),
};

export const usersApi = {
  list: () => get<UserResponse[]>('/api/users'),
  create: (body: CreateUserRequest) => post<UserResponse>('/api/users', body),
  remove: (id: string) => del<void>(`/api/users/${encodeURIComponent(id)}`),
  updateRoles: (id: string, body: UpdateUserRolesRequest) =>
    patch<UserResponse>(`/api/users/${encodeURIComponent(id)}/roles`, body),
};

export interface AuditLogQuery {
  actor?: string;
  action?: AuditAction;
  engine?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export const auditApi = {
  list: (q: AuditLogQuery = {}) => {
    const params = new URLSearchParams();
    if (q.actor) params.set('actor', q.actor);
    if (q.action) params.set('action', q.action);
    if (q.engine) params.set('engine', q.engine);
    if (q.from) params.set('from', q.from);
    if (q.to) params.set('to', q.to);
    if (q.page !== undefined) params.set('page', String(q.page));
    if (q.size !== undefined) params.set('size', String(q.size));
    const qs = params.toString();
    return get<AuditLogPageResponse>(`/api/audit-logs${qs ? `?${qs}` : ''}`);
  },
};

// ---- WebSocket helper (logs/stream) ----
//
// The native browser WebSocket API does not allow custom headers. The
// SPEC requires the JWT in the Authorization header (not a query param).
// The accepted workaround is to put the token in the Sec-WebSocket-Protocol
// value — many JWT-aware WS endpoints accept it there. The backend's
// JwtAuthFilter is the consumer; for the mock, this hook is unused.

export function buildLogsStreamUrl(code: string): string {
  const wsBase = USE_MOCK
    ? 'ws://mock'
    : (import.meta.env.VITE_WS_BASE_URL as string | undefined) ??
      (BASE.startsWith('https')
        ? BASE.replace(/^https/, 'wss')
        : BASE.replace(/^http/, 'ws'));
  return `${wsBase}/api/engines/${encodeURIComponent(code)}/logs/stream`;
}
