import type { AuthState } from '../auth/types';

/**
 * Wire types — mirror the JSON shapes returned by the Spring Boot
 * backend (see OrderEngineController + DTOs in
 * com.BPL_Order_Engine_Admin.manager.engine.dto). Keep these in
 * sync with the backend; a runtime check on `status` discriminates
 * the engine state.
 */

export type EngineStatus = 'RUNNING' | 'STOPPED' | 'ERROR';

export interface EngineStatusResponse {
  engineId: string;
  displayName: string;
  status: EngineStatus;
  lastTransitionAt: string | null;
  checkedAt: string;
}

export interface EngineActionResponse {
  engineId: string;
  displayName: string;
  status: EngineStatus;
  message: string;
  transitionedAt: string;
}

export interface LogLine {
  timestamp: string;
  level: string;
  message: string;
}

export interface LogPageResponse {
  engineId: string;
  limit: number;
  count: number;
  lines: LogLine[];
}

/** Standard error envelope returned by ApiExceptionHandler. */
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}

/** Thrown for non-2xx responses. {@link status} is the HTTP code. */
export class ApiError extends Error {
  readonly status: number;
  readonly body: ApiErrorBody | null;
  constructor(status: number, message: string, body: ApiErrorBody | null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }
}

/**
 * Base URL for the backend. The Vite dev server proxies /api → 8080
 * via vite.config.ts in production setups; for the local dev workflow
 * we hit the backend directly on 8080.
 *
 * Override with VITE_API_BASE_URL at build/dev time if you need to
 * point at a different host.
 */
const API_BASE: string =
  (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? 'http://localhost:8080';

/** Default engine id. The backend only registers "bpl" in Phase 1. */
export const ENGINE_ID = 'bpl';

interface FetchOptions {
  method?: 'GET' | 'POST';
  signal?: AbortSignal;
  body?: unknown;
}

async function request<T>(
  auth: AuthState,
  path: string,
  options: FetchOptions = {}
): Promise<T> {
  const { method = 'GET', signal, body } = options;
  const headers: Record<string, string> = {
    Authorization: `Basic ${auth.authorizationHeader}`,
    Accept: 'application/json',
  };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  const init: RequestInit = { method, headers, signal };
  if (body !== undefined) {
    init.body = JSON.stringify(body);
  }

  const res = await fetch(`${API_BASE}${path}`, init);

  if (!res.ok) {
    // Try to parse the standard error envelope; if the body isn't
    // JSON (e.g. proxy returned HTML), fall back to the status text.
    let errBody: ApiErrorBody | null = null;
    try {
      errBody = (await res.json()) as ApiErrorBody;
    } catch {
      /* not JSON — leave errBody null */
    }
    const message = errBody?.message ?? res.statusText ?? `HTTP ${res.status}`;
    throw new ApiError(res.status, message, errBody);
  }

  // 204 No Content (logout) has no body.
  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

/** GET /api/engines/{engineId}/status */
export function fetchStatus(auth: AuthState, signal?: AbortSignal): Promise<EngineStatusResponse> {
  return request<EngineStatusResponse>(auth, `/api/engines/${ENGINE_ID}/status`, { signal });
}

/** POST /api/engines/{engineId}/start */
export function startEngine(auth: AuthState, signal?: AbortSignal): Promise<EngineActionResponse> {
  return request<EngineActionResponse>(auth, `/api/engines/${ENGINE_ID}/start`, {
    method: 'POST',
    signal,
  });
}

/** POST /api/engines/{engineId}/stop */
export function stopEngine(auth: AuthState, signal?: AbortSignal): Promise<EngineActionResponse> {
  return request<EngineActionResponse>(auth, `/api/engines/${ENGINE_ID}/stop`, {
    method: 'POST',
    signal,
  });
}

/**
 * GET /api/engines/{engineId}/logs?limit=100
 * Only 50/100/200 are accepted by the backend; default 100.
 */
export function fetchLogs(
  auth: AuthState,
  limit: 50 | 100 | 200 = 100,
  signal?: AbortSignal
): Promise<LogPageResponse> {
  return request<LogPageResponse>(auth, `/api/engines/${ENGINE_ID}/logs?limit=${limit}`, { signal });
}
