import { FormEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  BashValidationResult,
  createEngine,
  EngineCreatePayload,
  validateEngine,
} from '../api/engines';
import { HostSummary, listHosts } from '../api/hosts';
import HostSelect from './HostSelect';
import ScriptConfirmationModal from './ScriptConfirmationModal';

const EMPTY_RESULT: BashValidationResult = {
  perScript: {},
  advisoryMatches: [],
};

export default function EngineAddForm() {
  const nav = useNavigate();
  const [hosts, setHosts] = useState<HostSummary[]>([]);
  const [name, setName] = useState('');
  const [hostId, setHostId] = useState<number | ''>('');
  const [startScript, setStartScript] = useState('');
  const [stopScript, setStopScript] = useState('');
  const [statusScript, setStatusScript] = useState('');
  const [logScript, setLogScript] = useState('');
  const [hostsError, setHostsError] = useState<string | null>(null);
  const [validation, setValidation] = useState<BashValidationResult | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    listHosts()
      .then(setHosts)
      .catch((e) => setHostsError((e as Error).message));
  }, []);

  const canValidate =
    name.trim() !== '' &&
    hostId !== '' &&
    startScript.trim() !== '' &&
    stopScript.trim() !== '' &&
    statusScript.trim() !== '' &&
    logScript.trim() !== '';

  function payload(): EngineCreatePayload {
    return {
      name: name.trim(),
      hostId: hostId as number,
      startScript,
      stopScript,
      statusScript,
      logScript,
    };
  }

  async function onSave(e: FormEvent) {
    e.preventDefault();
    if (!canValidate) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      const result = await validateEngine(payload());
      // If bash -n fails, the backend returns 400 with a BashValidationResult
      // body — apiFetch throws on non-2xx, so we never reach here with a
      // blocking failure. The modal still surfaces the result, and the
      // confirm button stays enabled as long as bash -n is green.
      setValidation(result);
    } catch (err) {
      // 400 from validate -> the server-side body IS the validation
      // result. The apiFetch wrapper throws ApiError with .body.
      const body = (err as { body?: BashValidationResult }).body;
      if (body && body.perScript) {
        setValidation(body);
      } else {
        setSubmitError((err as Error).message);
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function onConfirm() {
    setSubmitting(true);
    setSubmitError(null);
    try {
      await createEngine(payload());
      nav('/admin/engines', { replace: true });
    } catch (err) {
      setSubmitError((err as Error).message);
      setValidation(null);
    } finally {
      setSubmitting(false);
    }
  }

  function onCancelModal() {
    setValidation(null);
  }

  return (
    <>
      <h1 style={{ fontSize: 20, margin: '0 0 16px 0' }}>Add engine</h1>
      {hostsError && <div className="error-banner">{hostsError}</div>}
      {submitError && <div className="error-banner">{submitError}</div>}
      <form className="form" onSubmit={onSave} data-testid="engine-add-form">
        <div className="field">
          <label htmlFor="name">Name</label>
          <input
            id="name"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            data-testid="name-input"
          />
        </div>
        <div className="field">
          <label htmlFor="host">Host</label>
          <HostSelect hosts={hosts} value={hostId} onChange={setHostId} />
        </div>
        <div className="field">
          <label htmlFor="start">Start script</label>
          <textarea
            id="start"
            value={startScript}
            onChange={(e) => setStartScript(e.target.value)}
            required
            data-testid="start-script"
          />
        </div>
        <div className="field">
          <label htmlFor="stop">Stop script</label>
          <textarea
            id="stop"
            value={stopScript}
            onChange={(e) => setStopScript(e.target.value)}
            required
            data-testid="stop-script"
          />
        </div>
        <div className="field">
          <label htmlFor="status">Status script</label>
          <textarea
            id="status"
            value={statusScript}
            onChange={(e) => setStatusScript(e.target.value)}
            required
            data-testid="status-script"
          />
        </div>
        <div className="field">
          <label htmlFor="log">Log script</label>
          <textarea
            id="log"
            value={logScript}
            onChange={(e) => setLogScript(e.target.value)}
            required
            data-testid="log-script"
          />
        </div>
        <div className="form-actions">
          <button
            type="submit"
            className="button"
            disabled={!canValidate || submitting}
            data-testid="save-button"
          >
            {submitting ? 'Validating…' : 'Save'}
          </button>
          <button
            type="button"
            className="button secondary"
            onClick={() => nav('/admin/engines')}
            disabled={submitting}
          >
            Cancel
          </button>
        </div>
      </form>

      {validation && (
        <ScriptConfirmationModal
          result={validation || EMPTY_RESULT}
          onConfirm={onConfirm}
          onCancel={onCancelModal}
          busy={submitting}
        />
      )}
    </>
  );
}
