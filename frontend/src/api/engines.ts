import { apiFetch } from './client';

export interface Engine {
  id: number;
  name: string;
  hostId: number;
  hostAlias: string;
  hostnameOrIp: string;
  port: number;
}

export interface ScriptCheck {
  exitCode: number;
  stderr: string;
}

export interface AdvisoryMatch {
  script: 'start' | 'stop' | 'status' | 'log' | string;
  pattern: string;
  line: number;
}

export interface BashValidationResult {
  perScript: Record<string, ScriptCheck>;
  advisoryMatches: AdvisoryMatch[];
}

export interface EngineCreatePayload {
  name: string;
  hostId: number;
  startScript: string;
  stopScript: string;
  statusScript: string;
  logScript: string;
}

export function listEngines(): Promise<Engine[]> {
  return apiFetch<Engine[]>('/api/admin/engines');
}

export function validateEngine(
  payload: EngineCreatePayload
): Promise<BashValidationResult> {
  return apiFetch<BashValidationResult>('/api/admin/engines/validate', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function createEngine(payload: EngineCreatePayload): Promise<Engine> {
  return apiFetch<Engine>('/api/admin/engines', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}
