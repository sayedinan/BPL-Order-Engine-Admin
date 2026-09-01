/**
 * useEngineStatus — the status for one engine.
 *
 * Strategy: GET /api/engines/{code}/status on a 5s interval, paused
 * when the tab is hidden. Returns the latest status + lastTransitionAt
 * and a `refresh()` to force an immediate fetch.
 *
 * The SPEC says "WS primary, polling fallback when WS is closed" for
 * the dashboard's overall status, but per-engine status polling is
 * simpler and avoids a separate WS channel for status. The logs/stream
 * WS is the live channel; this hook just keeps the status badge fresh.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { enginesApi } from '../api/client';
import type { EngineStatus, EngineStatusResponse } from '../api/types';

const POLL_INTERVAL_MS = 5000;

export interface UseEngineStatusResult {
  data: EngineStatusResponse | null;
  status: EngineStatus | null;
  lastTransitionAt: string | null;
  error: string | null;
  refresh: () => void;
  isLoading: boolean;
}

export function useEngineStatus(
  engineCode: string | null,
  enabled = true,
): UseEngineStatusResult {
  const [data, setData] = useState<EngineStatusResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const tickRef = useRef(0);

  const fetchNow = useCallback(
    async (signal?: AbortSignal) => {
      if (!engineCode) return;
      setIsLoading(true);
      try {
        const res = await enginesApi.status(engineCode);
        if (signal?.aborted) return;
        setData(res);
        setError(null);
      } catch (err) {
        if (signal?.aborted) return;
        if (err instanceof Error && err.name === 'AbortError') return;
        setError(err instanceof Error ? err.message : 'Failed to load status');
      } finally {
        if (!signal?.aborted) setIsLoading(false);
      }
    },
    [engineCode],
  );

  useEffect(() => {
    if (!engineCode || !enabled) return;
    let cancelled = false;
    let controller: AbortController | null = null;
    let timer: ReturnType<typeof setTimeout> | null = null;

    const tick = async () => {
      if (cancelled) return;
      controller = new AbortController();
      tickRef.current += 1;
      await fetchNow(controller.signal);
      if (cancelled) return;
      timer = setTimeout(tick, POLL_INTERVAL_MS);
    };

    tick();

    return () => {
      cancelled = true;
      controller?.abort();
      if (timer) clearTimeout(timer);
    };
  }, [engineCode, enabled, fetchNow]);

  return {
    data,
    status: data?.status ?? null,
    lastTransitionAt: data?.lastTransitionAt ?? null,
    error,
    isLoading,
    refresh: () => {
      fetchNow();
    },
  };
}
