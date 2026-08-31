import { apiFetch } from '../api/client';

export interface CurrentUser {
  username: string;
  role: string;
}

export function fetchMe(): Promise<CurrentUser | null> {
  return apiFetch<CurrentUser | null>('/api/me');
}

export async function login(
  username: string,
  password: string
): Promise<CurrentUser> {
  // Spring Security form login expects application/x-www-form-urlencoded
  // with `username` and `password` fields. The apiFetch wrapper sends
  // JSON, so use a separate fetch here.
  const form = new URLSearchParams({ username, password });
  const res = await fetch('/api/login', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: form.toString(),
  });
  if (!res.ok) {
    throw new Error(res.status === 401 ? 'bad_credentials' : `HTTP ${res.status}`);
  }
  return fetchMe().then((u) => u as CurrentUser);
}

export async function logout(): Promise<void> {
  await fetch('/api/logout', { method: 'POST', credentials: 'include' });
}
