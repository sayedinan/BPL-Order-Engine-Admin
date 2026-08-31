import { apiFetch } from './client';

export interface HostSummary {
  id: number;
  alias: string;
  hostnameOrIp: string;
  port: number;
}

export function listHosts(): Promise<HostSummary[]> {
  return apiFetch<HostSummary[]>('/api/admin/hosts');
}
