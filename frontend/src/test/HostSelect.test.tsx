import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import HostSelect from '../admin/HostSelect';
import { HostSummary } from '../api/hosts';

const HOSTS: HostSummary[] = [
  { id: 1, alias: 'web-tier-host', hostnameOrIp: 'web.invalid', port: 22 },
  { id: 2, alias: 'worker-tier-host', hostnameOrIp: 'worker.invalid', port: 22 },
  { id: 3, alias: 'isolated-host', hostnameOrIp: 'isolated.invalid', port: 22022 },
];

describe('HostSelect', () => {
  it('renders one option per host with "alias — host:port"', () => {
    render(<HostSelect hosts={HOSTS} value="" onChange={() => {}} />);
    expect(screen.getByRole('option', { name: /web-tier-host — web.invalid:22/ })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: /worker-tier-host — worker.invalid:22/ })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: /isolated-host — isolated.invalid:22022/ })).toBeInTheDocument();
  });

  it('renders a placeholder when no host is selected', () => {
    render(<HostSelect hosts={HOSTS} value="" onChange={() => {}} />);
    expect(screen.getByRole('option', { name: /Select a host/ })).toBeInTheDocument();
  });

  it('invokes onChange with the host id when a host is chosen', async () => {
    const onChange = vi.fn();
    render(<HostSelect hosts={HOSTS} value="" onChange={onChange} />);
    await userEvent.selectOptions(screen.getByTestId('host-select'), '2');
    expect(onChange).toHaveBeenCalledWith(2);
  });

  it('shows the empty-state placeholder when there are no hosts', () => {
    render(<HostSelect hosts={[]} value="" onChange={() => {}} />);
    expect(screen.getByRole('option', { name: /No hosts available/ })).toBeInTheDocument();
  });

  it('disables the select when disabled prop is true', () => {
    render(<HostSelect hosts={HOSTS} value="" onChange={() => {}} disabled />);
    expect(screen.getByTestId('host-select')).toBeDisabled();
  });
});
