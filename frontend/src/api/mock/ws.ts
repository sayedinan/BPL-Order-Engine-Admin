/**
 * Mock WebSocket for the logs/stream endpoint.
 *
 * In mock mode, we don't use a real WebSocket — the heartbeats in the
 * store are already pushing to subscribers via a callback fan-out.
 * This module exposes a `MockLogsSocket` class that mirrors the parts
 * of the WebSocket API the React hook depends on: `onopen`,
 * `onmessage`, `onclose`, `close()`, and a `readyState`. The hook
 * doesn't need to know it's a mock.
 *
 * Auth: the WS handshake in real mode would use a header; in mock
 * mode the subscriber pattern is internal so the username is passed
 * in directly (the hook gets it from the AuthContext).
 */
import { recentLines, subscribeLogs, usernameForToken, store, type LogLine } from './store';

export const MOCK_WS_OPEN = 1;
export const MOCK_WS_CLOSED = 3;

export type WsStatus = 'connecting' | 'open' | 'reconnecting' | 'closed';

export interface MockSocketCallbacks {
  onOpen?: () => void;
  onMessage?: (line: LogLine) => void;
  onClose?: (reason: string) => void;
  onError?: (err: Error) => void;
}

/**
 * Open a mock WebSocket-like subscription to /api/engines/{code}/logs/stream.
 *
 * Wire callbacks via `.on({...})`, then call `.open()`. The socket
 * returns a snapshot of the last 100 lines immediately on open, then
 * pushes live updates until `.close()`.
 */
export class MockLogsSocket {
  code: string;
  auth: string | null;
  callbacks: MockSocketCallbacks = {};
  status: WsStatus = 'connecting';
  readyState: number = 0;
  private unsubscribe: (() => void) | null = null;

  constructor(code: string, auth: string | null) {
    this.code = code;
    this.auth = auth;
  }

  /** Wire the callbacks; returns `this` for chaining. */
  on(cbs: MockSocketCallbacks): this {
    this.callbacks = { ...this.callbacks, ...cbs };
    return this;
  }

  /** Open the connection. */
  open(): void {
    this.status = 'connecting';
    this.readyState = 0;
    const username = usernameForToken(this.auth);
    if (!username) {
      this.status = 'closed';
      this.readyState = MOCK_WS_CLOSED;
      setTimeout(() => this.callbacks.onClose?.('auth_failed'), 0);
      return;
    }
    const user = store.users.find((u) => u.username === username);
    if (!user) {
      this.status = 'closed';
      this.readyState = MOCK_WS_CLOSED;
      setTimeout(() => this.callbacks.onClose?.('auth_failed'), 0);
      return;
    }
    const engine = store.engines.find((e) => e.code === this.code);
    if (!engine) {
      this.status = 'closed';
      this.readyState = MOCK_WS_CLOSED;
      setTimeout(() => this.callbacks.onClose?.('engine_deleted'), 0);
      return;
    }
    if (user.role === 'USER' && !user.assignedEngineCodes.includes(this.code)) {
      this.status = 'closed';
      this.readyState = MOCK_WS_CLOSED;
      setTimeout(() => this.callbacks.onClose?.('forbidden'), 0);
      return;
    }
    // If the engine is stopped, send a snapshot only and close.
    if (engine.status === 'STOPPED') {
      for (const line of recentLines(this.code, 100)) {
        this.callbacks.onMessage?.(line);
      }
      this.status = 'closed';
      this.readyState = MOCK_WS_CLOSED;
      setTimeout(() => this.callbacks.onClose?.('engine_stopped'), 50);
      return;
    }

    // Snapshot: last 100 lines.
    for (const line of recentLines(this.code, 100)) {
      this.callbacks.onMessage?.(line);
    }
    // Subscribe to live updates.
    this.unsubscribe = subscribeLogs(this.code, (line) => {
      this.callbacks.onMessage?.(line);
    });
    this.status = 'open';
    this.readyState = MOCK_WS_OPEN;
    this.callbacks.onOpen?.();
  }

  /** Close the connection. Idempotent. */
  close(): void {
    if (this.unsubscribe) {
      this.unsubscribe();
      this.unsubscribe = null;
    }
    this.status = 'closed';
    this.readyState = MOCK_WS_CLOSED;
    this.callbacks.onClose?.('client_closed');
  }
}
