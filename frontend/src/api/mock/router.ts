/**
 * Mock fetch router. Intercepts the request the api/client makes and
 * returns a synthetic `Response` with the v0.3 envelope shape.
 *
 * Coverage: every endpoint in SPEC §4 (Auth, Engines, Users, Audit).
 * Missing route -> 404 with the standard envelope. Auth failures
 * surface as 401 (not 403) so the client treats them as "log out."
 *
 * The router is stateful: it mutates the in-memory `store` for
 * create/delete/patch actions, and pushes to the audit log on
 * state-changing calls.
 */
import type {
  AuditLogPageResponse,
  ChangePasswordRequest,
  CreateEngineRequest,
  CreateUserRequest,
  EngineActionResponse,
  EngineStatusResponse,
  LogPageResponse,
  LoginRequest,
  LoginResponse,
  UpdateEngineSshRequest,
  UpdateUserRolesRequest,
} from '../types';
import { makeError } from '../types';
import {
  appendAudit,
  checkCredentials,
  issueToken,
  revokeToken,
  startHeartbeat,
  stopHeartbeat,
  store,
  toEngineResponse,
  toUserResponse,
  usernameForToken,
} from './store';

const ROLE_RANK: Record<string, number> = { USER: 1, ADMIN: 2, SYS_ADMIN: 3 };

interface MockRequest {
  method: string;
  path: string;
  body: unknown;
  auth: string | null;
}

interface Handler {
  (req: MockRequest): Response;
}

const ALLOWED_LOG_LIMITS = new Set([50, 100, 200]);

function ok(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function err(
  status: number,
  message: string,
  path: string,
  details?: Record<string, unknown>,
): Response {
  return new Response(JSON.stringify(makeError(status, message, path, details)), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/** Require an authenticated user. Returns the response on failure, or
 * the UserResponse on success. */
function requireUser(auth: string | null, path: string):
  | { ok: true; user: ReturnType<typeof toUserResponse> }
  | { ok: false; response: Response } {
  const username = usernameForToken(auth);
  if (!username) {
    return { ok: false, response: err(401, 'Authentication required', path) };
  }
  const u = store.users.find((x) => x.username === username);
  if (!u) {
    return { ok: false, response: err(401, 'Authentication required', path) };
  }
  return { ok: true, user: toUserResponse(u) };
}

/** Require a minimum role. */
function requireRole(
  auth: string | null,
  minRole: 'USER' | 'ADMIN' | 'SYS_ADMIN',
  path: string,
):
  | { ok: true; user: ReturnType<typeof toUserResponse> }
  | { ok: false; response: Response } {
  const result = requireUser(auth, path);
  if (!result.ok) return result;
  const have = ROLE_RANK[result.user.role] ?? 0;
  const need = ROLE_RANK[minRole] ?? 99;
  if (have < need) {
    return { ok: false, response: err(403, 'Access denied', path) };
  }
  return result;
}

// ---- Route handlers ----

const handlers: Array<{ method: string; pattern: RegExp; handle: Handler }> = [];

function route(method: string, pattern: RegExp, handle: Handler) {
  handlers.push({ method, pattern, handle });
}

// ---- Auth ----

route('POST', /^\/api\/auth\/login$/, (req) => {
  const body = req.body as Partial<LoginRequest> | null;
  if (!body?.username || !body?.password) {
    return err(400, 'Username and password are required', req.path);
  }
  const user = checkCredentials(body.username, body.password);
  if (!user) {
    appendAudit({
      actorUsername: body.username,
      actorRole: 'USER',
      action: 'LOGIN_FAIL',
      targetEngineCode: null,
      details: { reason: 'BAD_CREDENTIALS' },
    });
    return err(401, 'Invalid credentials', req.path);
  }
  const token = issueToken(user.username);
  const expiresAt = new Date(Date.now() + 8 * 60 * 60 * 1000).toISOString();
  appendAudit({
    actorUsername: user.username,
    actorRole: user.role,
    action: 'LOGIN_SUCCESS',
    targetEngineCode: null,
    details: { reason: 'OK' },
  });
  const response: LoginResponse = {
    token,
    expiresAt,
    user: toUserResponse(user),
    mustChangePassword: user.mustChangePassword,
  };
  return ok(response);
});

route('POST', /^\/api\/auth\/logout$/, (req) => {
  const username = usernameForToken(req.auth);
  if (username) {
    const u = store.users.find((x) => x.username === username);
    appendAudit({
      actorUsername: username,
      actorRole: u?.role ?? 'USER',
      action: 'LOGOUT',
      targetEngineCode: null,
      details: {},
    });
  }
  revokeToken(req.auth);
  return new Response(null, { status: 204 });
});

route('GET', /^\/api\/auth\/me$/, (req) => {
  const result = requireUser(req.auth, req.path);
  if (!result.ok) return result.response;
  return ok(result.user);
});

route('POST', /^\/api\/auth\/change-password$/, (req) => {
  const authResult = requireUser(req.auth, req.path);
  if (!authResult.ok) return authResult.response;
  const body = req.body as Partial<ChangePasswordRequest> | null;
  if (!body?.currentPassword || !body?.newPassword) {
    return err(400, 'Both currentPassword and newPassword are required', req.path);
  }
  const u = store.users.find((x) => x.username === authResult.user.username);
  if (!u) {
    return err(401, 'Current password is incorrect', req.path);
  }
  // Mock: verify against the override (set by a prior change-password
  // in this session) or fall back to the seed convention. The override
  // has to win — otherwise the second change-password in a session
  // rejects the current password even though it was just set.
  const currentMatches =
    u.passwordOverride !== undefined
      ? body.currentPassword === u.passwordOverride
      : body.currentPassword === `${u.username}123`;
  if (!currentMatches) {
    appendAudit({
      actorUsername: u.username,
      actorRole: u.role,
      action: 'CHANGE_PASSWORD',
      targetEngineCode: null,
      details: { reason: 'BAD_CURRENT_PASSWORD' },
    });
    return err(401, 'Current password is incorrect', req.path);
  }
  // Validate the new password (mirrors backend's PasswordStrength rule).
  if (body.newPassword.length < 12 || !/[A-Za-z]/.test(body.newPassword) || !/\d/.test(body.newPassword)) {
    return err(
      422,
      'Password must be at least 12 characters and include a letter and a digit',
      req.path,
    );
  }
  u.mustChangePassword = false;
  // Persist the new password for subsequent logins in this session
  // (mirrors the backend's save+login flow). Stays in memory only.
  u.passwordOverride = body.newPassword;
  u.updatedAt = new Date().toISOString();
  appendAudit({
    actorUsername: u.username,
    actorRole: u.role,
    action: 'CHANGE_PASSWORD',
    targetEngineCode: null,
    details: { reason: 'OK' },
  });
  // Issue a new token (the old one stays valid until its natural expiry
  // but the client will replace it).
  const newToken = issueToken(u.username);
  const response: LoginResponse = {
    token: newToken,
    expiresAt: new Date(Date.now() + 8 * 60 * 60 * 1000).toISOString(),
    user: toUserResponse(u),
    mustChangePassword: false,
  };
  return ok(response);
});

// ---- Engines ----

route('GET', /^\/api\/engines$/, (req) => {
  const result = requireUser(req.auth, req.path);
  if (!result.ok) return result.response;
  // USER sees only assigned engines; ADMIN/SYS_ADMIN see all.
  let list = store.engines;
  if (result.user.role === 'USER') {
    list = list.filter((e) => result.user.assignedEngineCodes.includes(e.code));
  }
  return ok(list.map(toEngineResponse));
});

route('POST', /^\/api\/engines$/, (req) => {
  const result = requireRole(req.auth, 'SYS_ADMIN', req.path);
  if (!result.ok) return result.response;
  const body = req.body as Partial<CreateEngineRequest> | null;
  if (
    !body?.code ||
    !body?.name ||
    !body?.mode ||
    !body?.serverIp ||
    !body?.serverUsername ||
    !body?.serverPassword
  ) {
    return err(400, 'Missing required field(s)', req.path);
  }
  if (store.engines.some((e) => e.code === body.code)) {
    return err(409, `Engine '${body.code}' already exists`, req.path);
  }
  const now = new Date().toISOString();
  const created = {
    id: `e-${body.code.toLowerCase()}-${Math.floor(Math.random() * 10000)}`,
    code: body.code,
    name: body.name,
    mode: body.mode,
    serverIp: body.serverIp,
    serverUsername: body.serverUsername,
    serverPassword: body.serverPassword,
    startScript: body.startScript ?? null,
    stopScript: body.stopScript ?? null,
    logScript: body.logScript ?? null,
    status: 'STOPPED' as const,
    lastTransitionAt: null,
    createdAt: now,
    updatedAt: now,
  };
  store.engines.push(created);
  store.logBuffers[created.code] = [];
  appendAudit({
    actorUsername: result.user.username,
    actorRole: result.user.role,
    action: 'CREATE_ENGINE',
    targetEngineCode: created.code,
    details: { engineCode: created.code, mode: created.mode },
  });
  return ok(toEngineResponse(created), 201);
});

route('DELETE', /^\/api\/engines\/([^/]+)$/, (req) => {
  const result = requireRole(req.auth, 'SYS_ADMIN', req.path);
  if (!result.ok) return result.response;
  const code = decodeURIComponent(req.path.match(/^\/api\/engines\/([^/]+)$/)![1]);
  const engine = store.engines.find((e) => e.code === code);
  if (!engine) return err(404, `Engine '${code}' is not supported`, req.path);
  // Soft delete: just remove from the active list. The mock doesn't
  // preserve deleted rows, but the contract is honored (factory would
  // exclude them).
  store.engines = store.engines.filter((e) => e.code !== code);
  stopHeartbeat(code);
  appendAudit({
    actorUsername: result.user.username,
    actorRole: result.user.role,
    action: 'DELETE_ENGINE',
    targetEngineCode: code,
    details: { engineCode: code },
  });
  return new Response(null, { status: 204 });
});

route('PATCH', /^\/api\/engines\/([^/]+)\/ssh$/, (req) => {
  const result = requireRole(req.auth, 'SYS_ADMIN', req.path);
  if (!result.ok) return result.response;
  const code = decodeURIComponent(req.path.match(/^\/api\/engines\/([^/]+)\/ssh$/)![1]);
  const engine = store.engines.find((e) => e.code === code);
  if (!engine) return err(404, `Engine '${code}' is not supported`, req.path);
  const body = (req.body ?? {}) as UpdateEngineSshRequest;
  const changed: string[] = [];
  if (body.name !== undefined) {
    engine.name = body.name;
    changed.push('name');
  }
  if (body.mode !== undefined) {
    engine.mode = body.mode;
    changed.push('mode');
  }
  if (body.serverIp !== undefined) {
    engine.serverIp = body.serverIp;
    changed.push('serverIp');
  }
  if (body.serverUsername !== undefined) {
    engine.serverUsername = body.serverUsername;
    changed.push('serverUsername');
  }
  if (body.serverPassword !== undefined) {
    engine.serverPassword = body.serverPassword;
    // The audit row never carries the new password value — only the
    // fact that the field changed.
    changed.push('serverPassword');
  }
  if (body.startScript !== undefined) {
    engine.startScript = body.startScript;
    changed.push('startScript');
  }
  if (body.stopScript !== undefined) {
    engine.stopScript = body.stopScript;
    changed.push('stopScript');
  }
  if (body.logScript !== undefined) {
    engine.logScript = body.logScript;
    changed.push('logScript');
  }
  engine.updatedAt = new Date().toISOString();
  appendAudit({
    actorUsername: result.user.username,
    actorRole: result.user.role,
    action: 'UPDATE_ENGINE_SSH',
    targetEngineCode: code,
    details: { engineCode: code, fieldsChanged: changed },
  });
  return ok(toEngineResponse(engine));
});

route('GET', /^\/api\/engines\/([^/]+)\/status$/, (req) => {
  const result = requireUser(req.auth, req.path);
  if (!result.ok) return result.response;
  const code = decodeURIComponent(
    req.path.match(/^\/api\/engines\/([^/]+)\/status$/)![1],
  );
  const engine = store.engines.find((e) => e.code === code);
  if (!engine) return err(404, `Engine '${code}' is not supported`, req.path);
  if (result.user.role === 'USER' && !result.user.assignedEngineCodes.includes(code)) {
    return err(403, 'Access denied', req.path);
  }
  const response: EngineStatusResponse = {
    engineCode: engine.code,
    displayName: engine.name,
    status: engine.status,
    mode: engine.mode,
    lastTransitionAt: engine.lastTransitionAt,
    checkedAt: new Date().toISOString(),
  };
  return ok(response);
});

function engineAction(req: MockRequest, verb: 'start' | 'stop'): Response {
  const result = requireUser(req.auth, req.path);
  if (!result.ok) return result.response;
  const re = new RegExp(`^/api/engines/([^/]+)/${verb}$`);
  const code = decodeURIComponent(req.path.match(re)![1]);
  const engine = store.engines.find((e) => e.code === code);
  if (!engine) return err(404, `Engine '${code}' is not supported`, req.path);
  if (result.user.role === 'USER' && !result.user.assignedEngineCodes.includes(code)) {
    return err(403, 'Access denied', req.path);
  }
  if (verb === 'start' && engine.status === 'RUNNING') {
    appendAudit({
      actorUsername: result.user.username,
      actorRole: result.user.role,
      action: 'START_ENGINE',
      targetEngineCode: code,
      details: { engineCode: code, error: 'Conflict', message: 'Already running' },
    });
    return err(409, `Engine '${code}' is already RUNNING`, req.path);
  }
  if (verb === 'stop' && engine.status === 'STOPPED') {
    appendAudit({
      actorUsername: result.user.username,
      actorRole: result.user.role,
      action: 'STOP_ENGINE',
      targetEngineCode: code,
      details: { engineCode: code, error: 'Conflict', message: 'Already stopped' },
    });
    return err(409, `Engine '${code}' is already STOPPED`, req.path);
  }
  const now = new Date().toISOString();
  engine.status = verb === 'start' ? 'RUNNING' : 'STOPPED';
  engine.lastTransitionAt = now;
  engine.updatedAt = now;
  if (verb === 'start') {
    startHeartbeat(code);
  } else {
    stopHeartbeat(code);
  }
  appendAudit({
    actorUsername: result.user.username,
    actorRole: result.user.role,
    action: verb === 'start' ? 'START_ENGINE' : 'STOP_ENGINE',
    targetEngineCode: code,
    details: { engineCode: code, exitCode: 0 },
  });
  const response: EngineActionResponse = {
    engineCode: engine.code,
    displayName: engine.name,
    status: engine.status,
    message: `${engine.name} ${verb === 'start' ? 'started' : 'stopped'}.`,
    transitionedAt: now,
    exitCode: 0,
  };
  return ok(response);
}

route('POST', /^\/api\/engines\/([^/]+)\/start$/, (req) => engineAction(req, 'start'));
route('POST', /^\/api\/engines\/([^/]+)\/stop$/, (req) => engineAction(req, 'stop'));

route('GET', /^\/api\/engines\/([^/]+)\/logs$/, (req) => {
  const result = requireUser(req.auth, req.path);
  if (!result.ok) return result.response;
  const code = decodeURIComponent(req.path.match(/^\/api\/engines\/([^/]+)\/logs$/)![1]);
  const engine = store.engines.find((e) => e.code === code);
  if (!engine) return err(404, `Engine '${code}' is not supported`, req.path);
  if (result.user.role === 'USER' && !result.user.assignedEngineCodes.includes(code)) {
    return err(403, 'Access denied', req.path);
  }
  const url = new URL(req.path, 'http://mock');
  const limitParam = url.searchParams.get('limit');
  const limit = limitParam ? Number(limitParam) : 100;
  if (!ALLOWED_LOG_LIMITS.has(limit)) {
    return err(400, 'limit must be one of 50, 100, 200', req.path);
  }
  const buffer = store.logBuffers[code] ?? [];
  const lines = buffer.slice(-limit);
  const response: LogPageResponse = {
    engineCode: code,
    limit,
    count: lines.length,
    lines,
  };
  return ok(response);
});

// ---- Users ----

route('GET', /^\/api\/users$/, (req) => {
  const result = requireRole(req.auth, 'ADMIN', req.path);
  if (!result.ok) return result.response;
  return ok(store.users.map(toUserResponse));
});

route('POST', /^\/api\/users$/, (req) => {
  const authResult = requireUser(req.auth, req.path);
  if (!authResult.ok) return authResult.response;
  // ADMIN can only create USER; SYS_ADMIN can create anyone.
  const body = req.body as Partial<CreateUserRequest> | null;
  if (
    !body?.username ||
    !body?.password ||
    !body?.role ||
    !Array.isArray(body.assignedEngineCodes)
  ) {
    return err(400, 'Missing required field(s)', req.path);
  }
  if (authResult.user.role === 'ADMIN' && body.role !== 'USER') {
    return err(403, 'Access denied', req.path);
  }
  if (store.users.some((u) => u.username === body.username)) {
    return err(409, `Username '${body.username}' is taken`, req.path);
  }
  const now = new Date().toISOString();
  const newUser = {
    id: `u-${body.username}-${Math.floor(Math.random() * 10000)}`,
    username: body.username,
    role: body.role,
    assignedEngineCodes: [...body.assignedEngineCodes],
    mustChangePassword: true,
    createdAt: now,
    updatedAt: now,
  };
  store.users.push(newUser);
  appendAudit({
    actorUsername: authResult.user.username,
    actorRole: authResult.user.role,
    action: 'CREATE_USER',
    targetEngineCode: null,
    details: {
      newUserId: newUser.id,
      newUsername: newUser.username,
      newRole: newUser.role,
      assignedEngines: newUser.assignedEngineCodes,
    },
  });
  return ok(toUserResponse(newUser), 201);
});

route('DELETE', /^\/api\/users\/([^/]+)$/, (req) => {
  const authResult = requireUser(req.auth, req.path);
  if (!authResult.ok) return authResult.response;
  const id = decodeURIComponent(req.path.match(/^\/api\/users\/([^/]+)$/)![1]);
  if (id === authResult.user.id) {
    return err(400, 'You cannot delete yourself', req.path);
  }
  const target = store.users.find((u) => u.id === id);
  if (!target) return err(404, 'User not found', req.path);
  // ADMIN can only delete USER; SYS_ADMIN can delete anyone.
  if (authResult.user.role === 'ADMIN' && target.role !== 'USER') {
    return err(403, 'Access denied', req.path);
  }
  if (authResult.user.role !== 'SYS_ADMIN') {
    return err(403, 'Access denied', req.path);
  }
  // Prevent deleting the last SYS_ADMIN.
  if (target.role === 'SYS_ADMIN') {
    const remaining = store.users.filter(
      (u) => u.role === 'SYS_ADMIN' && u.id !== id,
    );
    if (remaining.length === 0) {
      return err(400, 'Cannot delete the last SYS_ADMIN', req.path);
    }
  }
  store.users = store.users.filter((u) => u.id !== id);
  appendAudit({
    actorUsername: authResult.user.username,
    actorRole: authResult.user.role,
    action: 'DELETE_USER',
    targetEngineCode: null,
    details: { targetUserId: id, targetUsername: target.username },
  });
  return new Response(null, { status: 204 });
});

route('PATCH', /^\/api\/users\/([^/]+)\/roles$/, (req) => {
  const authResult = requireUser(req.auth, req.path);
  if (!authResult.ok) return authResult.response;
  const id = decodeURIComponent(req.path.match(/^\/api\/users\/([^/]+)\/roles$/)![1]);
  const target = store.users.find((u) => u.id === id);
  if (!target) return err(404, 'User not found', req.path);
  if (authResult.user.role === 'ADMIN' && target.role !== 'USER') {
    return err(403, 'Access denied', req.path);
  }
  if (authResult.user.role !== 'SYS_ADMIN') {
    return err(403, 'Access denied', req.path);
  }
  const body = (req.body ?? {}) as UpdateUserRolesRequest;
  const oldRoles = [
    { roleType: target.role, assignedEngineCodes: [...target.assignedEngineCodes] },
  ];
  if (body.role !== undefined) {
    // The actor must be SYS_ADMIN (enforced above). They can set any role.
    target.role = body.role;
  }
  if (body.assignedEngineCodes !== undefined) {
    target.assignedEngineCodes = [...body.assignedEngineCodes];
  }
  target.updatedAt = new Date().toISOString();
  appendAudit({
    actorUsername: authResult.user.username,
    actorRole: authResult.user.role,
    action: 'UPDATE_USER_ROLES',
    targetEngineCode: null,
    details: {
      targetUserId: id,
      oldRoles,
      newRoles: [
        {
          roleType: target.role,
          assignedEngineCodes: target.assignedEngineCodes,
        },
      ],
    },
  });
  return ok(toUserResponse(target));
});

// ---- Audit log ----

route('GET', /^\/api\/audit-logs$/, (req) => {
  const result = requireRole(req.auth, 'ADMIN', req.path);
  if (!result.ok) return result.response;
  // USER is rejected outright (not filtered) per SPEC §4.5.
  const url = new URL(req.path, 'http://mock');
  const actor = url.searchParams.get('actor');
  const action = url.searchParams.get('action');
  const engine = url.searchParams.get('engine');
  const page = Math.max(0, Number(url.searchParams.get('page') ?? '0'));
  const size = Math.min(200, Math.max(1, Number(url.searchParams.get('size') ?? '50')));
  let items = store.auditLog;
  if (actor) items = items.filter((r) => r.actorUsername === actor);
  if (action) items = items.filter((r) => r.action === action);
  if (engine) items = items.filter((r) => r.targetEngineCode === engine);
  const total = items.length;
  const start = page * size;
  const slice = items.slice(start, start + size);
  const response: AuditLogPageResponse = {
    items: slice,
    page,
    size,
    total,
  };
  return ok(response);
});

// ---- Dispatcher ----

export async function handleMockRequest(input: Request): Promise<Response> {
  const req: MockRequest = {
    method: input.method,
    path: new URL(input.url).pathname + new URL(input.url).search,
    body:
      input.method === 'GET' || input.method === 'DELETE'
        ? null
        : await input.clone().json().catch(() => null),
    auth: input.headers.get('authorization'),
  };
  for (const h of handlers) {
    if (h.method !== req.method) continue;
    // Strip the query string before matching — every route regex is
    // anchored with `$` and would otherwise miss anything with `?...`.
    const m = req.path.split('?')[0].match(h.pattern);
    if (m) {
      // Re-derive path with first match group substituted in for nested
      // routes (e.g. /api/engines/BPL/start -> code=BPL). For the mock
      // we just hand the original path to the handler; the handler
      // re-extracts the code via its own regex.
      return h.handle(req);
    }
  }
  return err(404, `No mock route for ${req.method} ${req.path}`, req.path);
}
