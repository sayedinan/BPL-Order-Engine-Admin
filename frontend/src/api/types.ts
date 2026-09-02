/**
 * TypeScript mirrors of the v0.3 server DTOs (SPEC.md §4.6).
 * These are the single source of truth for the wire shape; both the
 * real API client and the mock layer conform to them.
 *
 * No `any`. No runtime validation — the v0.3 server validates and the
 * mock simulates the validated response. The shape is the contract.
 */

// ---- Common ----

export type Role = 'SYS_ADMIN' | 'ADMIN' | 'USER';
export type EngineStatus = 'RUNNING' | 'STOPPED' | 'ERROR';
export type EngineMode = 'MOCK' | 'REAL';

// ---- Audit log action enum (mirrors backend AuditAction) ----

export type AuditAction =
  | 'CREATE_USER'
  | 'DELETE_USER'
  | 'UPDATE_USER_ROLES'
  | 'CREATE_ENGINE'
  | 'DELETE_ENGINE'
  | 'UPDATE_ENGINE_SSH'
  | 'START_ENGINE'
  | 'STOP_ENGINE'
  | 'LOGIN_SUCCESS'
  | 'LOGIN_FAIL'
  | 'LOGOUT'
  | 'CHANGE_PASSWORD';

// ---- Auth ----

export interface UserResponse {
  id: string;
  username: string;
  role: Role;
  assignedEngineCodes: string[];
  /** Mirrors `User.mustChangePassword` from the live row. */
  mustChangePassword: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  expiresAt: string;
  user: UserResponse;
  mustChangePassword: boolean;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

// ---- Engine ----

export interface EngineResponse {
  id: string;
  code: string;
  name: string;
  mode: EngineMode;
  serverIp: string;
  serverUsername: string;
  // serverPassword is write-only — never present in responses.
  startScript: string | null;
  stopScript: string | null;
  logScript: string | null;
  status: EngineStatus;
  lastTransitionAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateEngineRequest {
  code: string;
  name: string;
  mode: EngineMode;
  serverIp: string;
  serverUsername: string;
  serverPassword: string;
  startScript?: string;
  stopScript?: string;
  logScript?: string;
}

export interface UpdateEngineSshRequest {
  name?: string;
  mode?: EngineMode;
  serverIp?: string;
  serverUsername?: string;
  serverPassword?: string;
  startScript?: string;
  stopScript?: string;
  logScript?: string;
}

export interface EngineStatusResponse {
  engineCode: string;
  displayName: string;
  status: EngineStatus;
  mode: EngineMode;
  lastTransitionAt: string | null;
  checkedAt: string;
}

export interface EngineActionResponse {
  engineCode: string;
  displayName: string;
  status: EngineStatus;
  message: string;
  transitionedAt: string;
  /**
   * Script exit code from the engine action. Always 0 on the success
   * path; failures throw `EngineScriptException` (HTTP 502) before
   * this DTO is produced. Exposed for symmetry with the audit row.
   */
  exitCode: number;
}

export interface LogLineResponse {
  timestamp: string;
  level: string;
  message: string;
}

export interface LogPageResponse {
  engineCode: string;
  limit: number;
  count: number;
  lines: LogLineResponse[];
}

// ---- User management ----

export interface CreateUserRequest {
  username: string;
  password: string;
  role: Role;
  assignedEngineCodes: string[];
}

export interface UpdateUserRolesRequest {
  role?: Role;
  assignedEngineCodes?: string[];
}

// ---- Audit log ----

export interface AuditLogResponse {
  id: string;
  timestamp: string;
  actorUsername: string;
  actorRole: Role;
  action: AuditAction;
  targetEngineCode: string | null;
  details: Record<string, unknown>;
}

export interface AuditLogPageResponse {
  items: AuditLogResponse[];
  page: number;
  size: number;
  total: number;
}

// ---- Error envelope (SPEC.md §4.1) ----

export interface ErrorEnvelope {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  details?: Record<string, unknown>;
}

// ---- Helpers ----

/** Make an ErrorEnvelope-shaped object. */
export function makeError(
  status: number,
  message: string,
  path: string,
  details?: Record<string, unknown>,
): ErrorEnvelope {
  return {
    timestamp: new Date().toISOString(),
    status,
    error: reasonFor(status),
    message,
    path,
    ...(details ? { details } : {}),
  };
}

function reasonFor(status: number): string {
  switch (status) {
    case 400:
      return 'Bad Request';
    case 401:
      return 'Unauthorized';
    case 403:
      return 'Forbidden';
    case 404:
      return 'Not Found';
    case 409:
      return 'Conflict';
    case 422:
      return 'Unprocessable Entity';
    case 502:
      return 'Bad Gateway';
    case 504:
      return 'Gateway Timeout';
    default:
      return status >= 500 ? 'Internal Server Error' : 'Error';
  }
}
