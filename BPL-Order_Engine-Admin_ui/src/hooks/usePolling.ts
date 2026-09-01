import { useEffect, useRef } from 'react';

/**
 * Run {@link fn} on an interval, only while the component is mounted.
 * The callback receives an {@code AbortSignal} so the in-flight fetch
 * can be cancelled when the timer fires again or the component
 * unmounts.
 *
 * The interval is paused while {@code enabled} is false; flipping it
 * back to true resumes immediately. Errors thrown from {@link fn} are
 * reported to the optional {@link onError} callback so the caller can
 * surface them in the UI; they do not stop the timer.
 */
export function usePolling(
  fn: (signal: AbortSignal) => Promise<void> | void,
  intervalMs: number,
  enabled: boolean,
  onError?: (err: unknown) => void
): void {
  const fnRef = useRef(fn);
  const onErrorRef = useRef(onError);

  // Keep the latest fn/onError available to the polling loop without
  // restarting the timer on every render. Per the react-hooks/refs
  // rule, ref mutations must be inside an effect or event handler —
  // not in the render body.
  useEffect(() => {
    fnRef.current = fn;
  }, [fn]);
  useEffect(() => {
    onErrorRef.current = onError;
  }, [onError]);

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    let controller: AbortController | null = null;
    let timer: ReturnType<typeof setTimeout> | null = null;

    const tick = async () => {
      if (cancelled) return;
      controller = new AbortController();
      try {
        await fnRef.current(controller.signal);
      } catch (err) {
        // AbortError on unmount is expected; ignore.
        if (controller?.signal.aborted) return;
        onErrorRef.current?.(err);
      } finally {
        if (!cancelled) {
          timer = setTimeout(tick, intervalMs);
        }
      }
    };

    tick();

    return () => {
      cancelled = true;
      controller?.abort();
      if (timer !== null) clearTimeout(timer);
    };
  }, [enabled, intervalMs]);
}
