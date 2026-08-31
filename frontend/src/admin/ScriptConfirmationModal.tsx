import { BashValidationResult } from '../api/engines';

interface Props {
  result: BashValidationResult;
  onConfirm: () => void;
  onCancel: () => void;
  busy: boolean;
}

/**
 * Mandatory confirmation modal shown after a successful
 * /api/admin/engines/validate call. Per SPEC §7.4 the modal is not
 * skippable: the only way to proceed is "Confirm and save".
 *
 * If any per-script `bash -n` returned non-zero, the "Confirm and
 * save" button is disabled — the validate endpoint should have
 * rejected the request with 400, but the disable is belt-and-braces
 * so a future regression cannot ship a bad script.
 */
export default function ScriptConfirmationModal({
  result,
  onConfirm,
  onCancel,
  busy,
}: Props) {
  const hasBlocking = result.perScript &&
    Object.values(result.perScript).some((c) => c.exitCode !== 0);

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true">
      <div className="modal">
        <h2>Confirm engine save</h2>
        <p style={{ margin: '0 0 12px 0', fontSize: 13, color: 'var(--muted)' }}>
          Review the bash safety check below. The save is gated on this step.
        </p>

        <h3 style={{ fontSize: 14, margin: '0 0 8px 0' }}>bash -n results</h3>
        {Object.entries(result.perScript || {}).map(([slot, check]) => (
          <pre
            key={slot}
            className={check.exitCode !== 0 ? 'has-error' : ''}
            data-testid={`bash-result-${slot}`}
          >
            <strong>{slot}</strong>: exit {check.exitCode}
            {check.stderr && `\n${check.stderr}`}
          </pre>
        ))}

        {result.advisoryMatches && result.advisoryMatches.length > 0 && (
          <div className="advisory" data-testid="advisory-list">
            <strong>Advisory patterns (informational — does not block):</strong>
            <ul>
              {result.advisoryMatches.map((m, i) => (
                <li key={i}>
                  <code>{m.script}</code>: {m.pattern} (line {m.line})
                </li>
              ))}
            </ul>
          </div>
        )}

        <div className="form-actions" style={{ justifyContent: 'flex-end' }}>
          <button
            type="button"
            className="button secondary"
            onClick={onCancel}
            disabled={busy}
            data-testid="modal-cancel"
          >
            Cancel
          </button>
          <button
            type="button"
            className="button"
            onClick={onConfirm}
            disabled={busy || hasBlocking}
            data-testid="modal-confirm"
          >
            {busy ? 'Saving…' : 'Confirm and save'}
          </button>
        </div>
      </div>
    </div>
  );
}
