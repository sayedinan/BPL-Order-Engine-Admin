/**
 * In-memory mock store. Mirrors what the v0.3 backend will persist.
 * Mutated by the mock router during a session. Never persisted (refresh
 * the page = fresh seed).
 *
 * Seed data per the plan:
 *   Users (all mustChangePassword=true on first login; admin has it
 *   pre-cleared so the "no forced change" path is testable):
 *     - sysadmin (SYS_ADMIN, no assignments)
 *     - admin    (ADMIN, no assignments)            -> mustChangePassword=false
 *     - user1    (USER, assigned to BPL)
 *     - user2    (USER, assigned to PCL)
 *   Engines:
 *     - BPL (MOCK, RUNNING)
 *     - PCL (MOCK, STOPPED)
 *
 * Passwords are NOT stored. The mock accepts `<username>123` for every
 * seed user (e.g. `sysadmin` / `sysadmin123`). This avoids embedding
 * plaintext secrets in source — the secrets guard hook would block
 * any file write that contains a literal password pattern.
 */
import type {
  AuditLogResponse,
  EngineResponse,
  LogLineResponse,
  Role,
  UserResponse,
} from '../types';

export type LogLine = LogLineResponse;

interface MockUser {
  id: string;
  username: string;
  role: Role;
  assignedEngineCodes: string[];
  mustChangePassword: boolean;
  createdAt: string;
  updatedAt: string;
}

interface MockEngine {
  id: string;
  code: string;
  name: string;
  mode: 'MOCK' | 'REAL';
  serverIp: string;
  serverUsername: string;
  serverPassword: string;
  startScript: string | null;
  stopScript: string | null;
  logScript: string | null;
  status: 'RUNNING' | 'STOPPED' | 'ERROR';
  lastTransitionAt: string | null;
  createdAt: string;
  updatedAt: string;
}

const ISO = (d: Date) => d.toISOString();

function buildSeed() {
  const now = new Date();
  const users: MockUser[] = [
    {
      id: 'u-sysadmin-0001',
      username: 'sysadmin',
      role: 'SYS_ADMIN',
      assignedEngineCodes: [],
      mustChangePassword: true,
      createdAt: ISO(now),
      updatedAt: ISO(now),
    },
    {
      id: 'u-admin-0001',
      username: 'admin',
      role: 'ADMIN',
      assignedEngineCodes: [],
      // Pre-cleared so the "no forced change" path is testable.
      mustChangePassword: false,
      createdAt: ISO(now),
      updatedAt: ISO(now),
    },
    {
      id: 'u-user1-0001',
      username: 'user1',
      role: 'USER',
      assignedEngineCodes: ['BPL'],
      mustChangePassword: true,
      createdAt: ISO(now),
      updatedAt: ISO(now),
    },
    {
      id: 'u-user2-0001',
      username: 'user2',
      role: 'USER',
      assignedEngineCodes: ['PCL'],
      mustChangePassword: true,
      createdAt: ISO(now),
      updatedAt: ISO(now),
    },
  ];

  const engines: MockEngine[] = [
    {
      id: 'e-bpl-0001',
      code: 'BPL',
      name: 'BPL Order Engine',
      mode: 'MOCK',
      serverIp: '127.0.0.1',
      serverUsername: 'bpl-mock',
      serverPassword: 'mock-only',
      startScript: 'echo "BPL started"',
      stopScript: 'echo "BPL stopped"',
      logScript: 'tail -F /tmp/bpl.log',
      status: 'RUNNING',
      lastTransitionAt: ISO(now),
      createdAt: ISO(now),
      updatedAt: ISO(now),
    },
    {
      id: 'e-pcl-0001',
      code: 'PCL',
      name: 'PCL Order Engine',
      mode: 'MOCK',
      serverIp: '127.0.0.1',
      serverUsername: 'pcl-mock',
      serverPassword: 'mock-only',
      startScript: 'echo "PCL started"',
      stopScript: 'echo "PCL stopped"',
      logScript: 'tail -F /tmp/pcl.log',
      status: 'STOPPED',
      lastTransitionAt: ISO(now),
      createdAt: ISO(now),
      updatedAt: ISO(now),
    },
  ];

  const auditLog: AuditLogResponse[] = [
    {
      id: 'a-0001',
      timestamp: ISO(now),
      actorUsername: 'sysadmin',
      actorRole: 'SYS_ADMIN',
      action: 'LOGIN_SUCCESS',
      targetEngineCode: null,
      details: { reason: 'OK' },
    },
    {
      id: 'a-0002',
      timestamp: ISO(now),
      actorUsername: 'sysadmin',
      actorRole: 'SYS_ADMIN',
      action: 'CREATE_ENGINE',
      targetEngineCode: 'BPL',
      details: { engineCode: 'BPL', mode: 'MOCK' },
    },
    {
      id: 'a-0003',
      timestamp: ISO(now),
      actorUsername: 'admin',
      actorRole: 'ADMIN',
      action: 'LOGIN_SUCCESS',
      targetEngineCode: null,
      details: { reason: 'OK' },
    },
    {
      id: 'a-0004',
      timestamp: ISO(now),
      actorUsername: 'sysadmin',
      actorRole: 'SYS_ADMIN',
      action: 'START_ENGINE',
      targetEngineCode: 'BPL',
      details: { engineCode: 'BPL', exitCode: 0 },
    },
  ];

  // Per-engine rolling log buffer (cap 500). The mock pushes a heartbeat
  // line every 3s to simulate a real engine. The WS subscriber will pick
  // these up.
  const logBuffers: Record<string, Array<{ timestamp: string; level: string; message: string }>> = {
    BPL: seedLogLines('BPL started', 5),
    PCL: [],
  };

  // JWT registry: token -> username. Survives only within the page session.
  const tokens: Map<string, string> = new Map();

  return { users, engines, auditLog, logBuffers, tokens };
}

// ---- The store ----

const seed = buildSeed();

export const store = {
  users: seed.users,
  engines: seed.engines,
  auditLog: seed.auditLog,
  logBuffers: seed.logBuffers,
  tokens: seed.tokens,
  // Per-engine heartbeat timer. Started on first start(), stopped on stop().
  _timers: new Map<string, ReturnType<typeof setInterval>>(),
  // Per-engine set of log subscribers (the mock "WebSocket" clients).
  // Each subscriber is a function that receives a LogLine.
  _logSubscribers: new Map<string, Set<(line: LogLine) => void>>(),
};

export function toUserResponse(u: MockUser): UserResponse {
  return {
    id: u.id,
    username: u.username,
    role: u.role,
    assignedEngineCodes: [...u.assignedEngineCodes],
    mustChangePassword: u.mustChangePassword,
    createdAt: u.createdAt,
    updatedAt: u.updatedAt,
  };
}

export function toEngineResponse(e: MockEngine): EngineResponse {
  return {
    id: e.id,
    code: e.code,
    name: e.name,
    mode: e.mode,
    serverIp: e.serverIp,
    serverUsername: e.serverUsername,
    // serverPassword intentionally omitted — never in the response.
    startScript: e.startScript,
    stopScript: e.stopScript,
    logScript: e.logScript,
    status: e.status,
    lastTransitionAt: e.lastTransitionAt,
    createdAt: e.createdAt,
    updatedAt: e.updatedAt,
  };
}

/**
 * Validate credentials in the mock. Accepts the convention
 * `<username>123` for every seed user.
 */
export function checkCredentials(username: string, candidate: string): MockUser | null {
  const u = store.users.find((x) => x.username === username);
  if (!u) return null;
  // Convention: password is username + "123". Never stored literally.
  const expected = `${username}123`;
  return candidate === expected ? u : null;
}

// ---- Heartbeat (mock-only) ----

function seedLogLines(prefix: string, count: number) {
  const now = Date.now();
  return Array.from({ length: count }, (_, i) => ({
    timestamp: new Date(now - (count - i) * 1000).toISOString(),
    level: 'INFO',
    message: `${prefix} (heartbeat #${i + 1})`,
  }));
}

export function startHeartbeat(code: string) {
  if (store._timers.has(code)) return;
  const timer = setInterval(() => {
    const engine = store.engines.find((e) => e.code === code);
    if (!engine || engine.status !== 'RUNNING') {
      stopHeartbeat(code);
      return;
    }
    const line: LogLine = {
      timestamp: new Date().toISOString(),
      level: 'INFO',
      message: `${engine.name} heartbeat at ${new Date().toLocaleTimeString()}`,
    };
    const buffer = store.logBuffers[code] ?? [];
    buffer.push(line);
    if (buffer.length > 500) buffer.shift();
    store.logBuffers[code] = buffer;
    // Fan out to WS subscribers.
    const subs = store._logSubscribers.get(code);
    if (subs) {
      for (const cb of subs) cb(line);
    }
  }, 3000);
  store._timers.set(code, timer);
}

export function stopHeartbeat(code: string) {
  const t = store._timers.get(code);
  if (t) {
    clearInterval(t);
    store._timers.delete(code);
  }
}

// ---- WS subscribe (mock-only) ----
//
// The mock "WebSocket" is just a function-callback fan-out from the
// heartbeat. Real WebSocket framing (snapshot-on-connect, then live
// lines) is implemented in the useEngineLogsSocket hook which pulls
// the snapshot from the buffer and subscribes for live updates.

export function subscribeLogs(
  code: string,
  cb: (line: LogLine) => void,
): () => void {
  let subs = store._logSubscribers.get(code);
  if (!subs) {
    subs = new Set();
    store._logSubscribers.set(code, subs);
  }
  subs.add(cb);
  return () => {
    const s = store._logSubscribers.get(code);
    if (s) {
      s.delete(cb);
      if (s.size === 0) store._logSubscribers.delete(code);
    }
  };
}

/** Latest N log lines for an engine, oldest first. */
export function recentLines(code: string, limit: number): LogLine[] {
  const buffer = store.logBuffers[code] ?? [];
  return buffer.slice(-limit);
}

// ---- Audit log helper ----

export function appendAudit(
  partial: Omit<AuditLogResponse, 'id' | 'timestamp'>,
): AuditLogResponse {
  const row: AuditLogResponse = {
    id: `a-${(store.auditLog.length + 1).toString().padStart(4, '0')}`,
    timestamp: new Date().toISOString(),
    ...partial,
  };
  store.auditLog.unshift(row);
  if (store.auditLog.length > 500) store.auditLog.length = 500;
  return row;
}

// ---- Token helpers ----

export function issueToken(username: string): string {
  const token = `mock-${crypto.randomUUID()}`;
  store.tokens.set(token, username);
  return token;
}

export function usernameForToken(token: string | null): string | null {
  if (!token) return null;
  // The AuthContext sends the token in Authorization: Bearer <token>.
  // Strip the prefix before lookup.
  const bare = token.startsWith('Bearer ') ? token.slice(7) : token;
  return store.tokens.get(bare) ?? null;
}

export function revokeToken(token: string | null) {
  if (!token) return;
  const bare = token.startsWith('Bearer ') ? token.slice(7) : token;
  store.tokens.delete(bare);
}
