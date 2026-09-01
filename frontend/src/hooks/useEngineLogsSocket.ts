/**
 * useEngineLogsSocket — subscribe to live log lines for an engine.
 *
 * In mock mode, uses the MockLogsSocket (a WebSocket-API-compatible
 * abstraction over the store's subscriber fan-out). In real mode,
 * uses the native browser WebSocket. The JWT travels in the
 * `Sec-WebSocket-Protocol` value (browsers don't allow custom
 * handshake headers).
 *
 * Reconnect: exponential backoff 1s → 30s cap on transient close.
 * No reconnect on `engine_stopped`, `engine_deleted`, `auth_failed`,
 * or `forbidden` (terminal).
 */
import { useEffect, useRef, useState } from 'react';
import { buildLogsStreamUrl, getToken } from '../api/client';
import type { LogLine } from '../api/mock/store';
import { MockLogsSocket } from '../api/mock/ws';

const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false';

export type WsStatus = 'connecting' | 'open' | 'reconnecting' | 'closed';

export interface UseEngineLogsSocketResult {
  lines: LogLine[];
  status: WsStatus;
  closeReason: string | null;
  reconnect: () => void;
}

const TERMINAL_REASONS = new Set([
  'engine_stopped',
  'engine_deleted',
  'auth_failed',
  'forbidden',
  'client_closed',
]);

const RECONNECT_DELAYS = [1000, 2000, 4000, 8000, 16000, 30000];

export function useEngineLogsSocket(
  engineCode: string | null,
): UseEngineLogsSocketResult {
  const [lines, setLines] = useState<LogLine[]>([]);
  const [status, setStatus] = useState<WsStatus>('connecting');
  const [closeReason, setCloseReason] = useState<string | null>(null);
  const attemptsRef = useRef(0);
  const socketRef = useRef<MockLogsSocket | WebSocket | null>(null);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const unmountedRef = useRef(false);

  useEffect(() => {
    unmountedRef.current = false;
    if (!engineCode) {
      return;
    }
    attemptsRef.current = 0;

    function teardown() {
      const sock = socketRef.current;
      if (sock) {
        if (USE_MOCK) {
          (sock as MockLogsSocket).close();
        } else {
          (sock as WebSocket).close();
        }
        socketRef.current = null;
      }
      if (reconnectTimerRef.current) {
        clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
    }

    function connect() {
      if (unmountedRef.current) return;
      setStatus(attemptsRef.current === 0 ? 'connecting' : 'reconnecting');

      if (USE_MOCK) {
        const token = getToken();
        const sock = new MockLogsSocket(engineCode!, token ? `Bearer ${token}` : null);
        sock.on({
          onOpen: () => {
            if (unmountedRef.current) return;
            attemptsRef.current = 0;
            setStatus('open');
          },
          onMessage: (line) => {
            if (unmountedRef.current) return;
            setLines((prev) => {
              const next = [...prev, line];
              if (next.length > 500) next.shift();
              return next;
            });
          },
          onClose: (reason) => {
            if (unmountedRef.current) return;
            setCloseReason(reason);
            if (TERMINAL_REASONS.has(reason)) {
              setStatus('closed');
              socketRef.current = null;
              return;
            }
            const delay =
              RECONNECT_DELAYS[
                Math.min(attemptsRef.current, RECONNECT_DELAYS.length - 1)
              ];
            attemptsRef.current++;
            setStatus('reconnecting');
            reconnectTimerRef.current = setTimeout(connect, delay);
          },
        });
        socketRef.current = sock;
        sock.open();
        return;
      }

      // Real WebSocket path.
      const url = buildLogsStreamUrl(engineCode!);
      const token = getToken();
      const protocols = token ? [`bearer.${token}`] : [];
      const ws = new WebSocket(url, protocols);
      socketRef.current = ws;

      ws.onopen = () => {
        if (unmountedRef.current) return;
        attemptsRef.current = 0;
        setStatus('open');
      };
      ws.onmessage = (ev) => {
        if (unmountedRef.current) return;
        try {
          const parsed = JSON.parse(ev.data) as
            | LogLine
            | { event: 'closed'; reason: string };
          if ('event' in parsed && parsed.event === 'closed') {
            setCloseReason(parsed.reason);
            if (TERMINAL_REASONS.has(parsed.reason)) {
              setStatus('closed');
              return;
            }
          } else {
            const line = parsed as LogLine;
            setLines((prev) => {
              const next = [...prev, line];
              if (next.length > 500) next.shift();
              return next;
            });
          }
        } catch {
          /* ignore malformed frames */
        }
      };
      ws.onerror = () => {
        /* onclose follows; we handle reconnect there */
      };
      ws.onclose = (ev) => {
        if (unmountedRef.current) return;
        const reason = ev.reason || 'network_dropped';
        setCloseReason(reason);
        if (TERMINAL_REASONS.has(reason) || ev.wasClean) {
          setStatus('closed');
          socketRef.current = null;
          return;
        }
        const delay =
          RECONNECT_DELAYS[
            Math.min(attemptsRef.current, RECONNECT_DELAYS.length - 1)
          ];
        attemptsRef.current++;
        setStatus('reconnecting');
        reconnectTimerRef.current = setTimeout(connect, delay);
      };
    }

    connect();

    return () => {
      unmountedRef.current = true;
      teardown();
    };
  }, [engineCode]);

  return {
    lines,
    status: engineCode ? status : 'closed',
    closeReason,
    reconnect: () => {
      const sock = socketRef.current;
      if (sock) {
        if (USE_MOCK) (sock as MockLogsSocket).close();
        else (sock as WebSocket).close();
      }
      attemptsRef.current = 0;
      setCloseReason(null);
    },
  };
}
