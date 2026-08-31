import { HostSummary } from '../api/hosts';

interface Props {
  hosts: HostSummary[];
  value: number | '';
  onChange: (id: number) => void;
  disabled?: boolean;
}

/**
 * Plain native <select> for the engine-add form. Per the approved
 * plan: no type-ahead, no optgroup, no combobox. ~10 hosts is the
 * realistic upper bound; a native select is the right primitive.
 *
 * Each option is rendered as "alias — hostname:port" so the operator
 * can tell two hosts on the same address apart.
 */
export default function HostSelect({ hosts, value, onChange, disabled }: Props) {
  return (
    <select
      data-testid="host-select"
      value={value}
      disabled={disabled}
      onChange={(e) => onChange(Number(e.target.value))}
    >
      <option value="" disabled>
        {hosts.length === 0 ? 'No hosts available' : 'Select a host…'}
      </option>
      {hosts.map((h) => (
        <option key={h.id} value={h.id}>
          {h.alias} — {h.hostnameOrIp}:{h.port}
        </option>
      ))}
    </select>
  );
}
