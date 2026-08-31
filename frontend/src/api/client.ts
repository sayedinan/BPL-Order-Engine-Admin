/**
 * Tiny fetch wrapper. Always sends the cookie session
 * (`credentials: 'include'`) and JSON. Throws on non-2xx with a
 * small object the UI can render.
 */
export class ApiError extends Error {
  status: number;
  body: unknown;
  constructor(status: number, message: string, body: unknown) {
    super(message);
    this.status = status;
    this.body = body;
  }
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {}
): Promise<T> {
  const res = await fetch(path, {
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      ...(init.body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(init.headers || {}),
    },
    ...init,
  });
  if (!res.ok) {
    let body: unknown = null;
    try {
      body = await res.json();
    } catch {
      /* ignore */
    }
    throw new ApiError(res.status, `HTTP ${res.status} on ${path}`, body);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}
