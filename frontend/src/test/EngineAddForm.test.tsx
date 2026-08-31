import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import EngineAddForm from '../admin/EngineAddForm';
import * as hostsApi from '../api/hosts';
import * as enginesApi from '../api/engines';

vi.mock('../api/hosts');
vi.mock('../api/engines');

const HOSTS = [
  { id: 1, alias: 'web-tier-host', hostnameOrIp: 'web.invalid', port: 22 },
];

function fillForm() {
  return userEvent
    .type(screen.getByTestId('name-input'), 'eng-test')
    .then(() => userEvent.selectOptions(screen.getByTestId('host-select'), '1'))
    .then(() => userEvent.type(screen.getByTestId('start-script'), 'echo start'))
    .then(() => userEvent.type(screen.getByTestId('stop-script'), 'echo stop'))
    .then(() => userEvent.type(screen.getByTestId('status-script'), 'echo status'))
    .then(() => userEvent.type(screen.getByTestId('log-script'), 'echo log'));
}

describe('EngineAddForm', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(hostsApi.listHosts).mockResolvedValue(HOSTS);
  });

  it('populates the host dropdown from listHosts()', async () => {
    render(<MemoryRouter><EngineAddForm /></MemoryRouter>);
    expect(await screen.findByRole('option', { name: /web-tier-host/ })).toBeInTheDocument();
  });

  it('Save button is disabled until all fields are non-blank and a host is selected', async () => {
    render(<MemoryRouter><EngineAddForm /></MemoryRouter>);
    await screen.findByRole('option', { name: /web-tier-host/ });
    expect(screen.getByTestId('save-button')).toBeDisabled();
    await fillForm();
    expect(screen.getByTestId('save-button')).toBeEnabled();
  });

  it('Save opens the confirmation modal which gates the create call', async () => {
    vi.mocked(enginesApi.validateEngine).mockResolvedValue({
      perScript: {
        start: { exitCode: 0, stderr: '' },
        stop: { exitCode: 0, stderr: '' },
        status: { exitCode: 0, stderr: '' },
        log: { exitCode: 0, stderr: '' },
      },
      advisoryMatches: [],
    });
    vi.mocked(enginesApi.createEngine).mockResolvedValue({
      id: 99,
      name: 'eng-test',
      hostId: 1,
      hostAlias: 'web-tier-host',
      hostnameOrIp: 'web.invalid',
      port: 22,
    });

    render(<MemoryRouter><EngineAddForm /></MemoryRouter>);
    await screen.findByRole('option', { name: /web-tier-host/ });
    await fillForm();
    await userEvent.click(screen.getByTestId('save-button'));

    // Modal opens; createEngine has NOT been called yet.
    expect(await screen.findByTestId('modal-confirm')).toBeInTheDocument();
    expect(enginesApi.createEngine).not.toHaveBeenCalled();

    // Confirm -> createEngine is called.
    await userEvent.click(screen.getByTestId('modal-confirm'));
    await waitFor(() => expect(enginesApi.createEngine).toHaveBeenCalledTimes(1));
  });

  it('Cancel in the modal closes the modal without calling createEngine', async () => {
    vi.mocked(enginesApi.validateEngine).mockResolvedValue({
      perScript: {
        start: { exitCode: 0, stderr: '' },
        stop: { exitCode: 0, stderr: '' },
        status: { exitCode: 0, stderr: '' },
        log: { exitCode: 0, stderr: '' },
      },
      advisoryMatches: [],
    });

    render(<MemoryRouter><EngineAddForm /></MemoryRouter>);
    await screen.findByRole('option', { name: /web-tier-host/ });
    await fillForm();
    await userEvent.click(screen.getByTestId('save-button'));
    await screen.findByTestId('modal-confirm');
    await userEvent.click(screen.getByTestId('modal-cancel'));
    await waitFor(() =>
      expect(screen.queryByTestId('modal-confirm')).not.toBeInTheDocument());
    expect(enginesApi.createEngine).not.toHaveBeenCalled();
  });

  it('Confirm button is disabled when bash -n reports a failure on any script', async () => {
    // Force the validate response to include a non-zero exit code
    // (the backend would normally return 400, but we simulate the
    // case where the modal renders with a blocking failure).
    vi.mocked(enginesApi.validateEngine).mockResolvedValue({
      perScript: {
        start: { exitCode: 0, stderr: '' },
        stop: { exitCode: 1, stderr: 'syntax error' },
        status: { exitCode: 0, stderr: '' },
        log: { exitCode: 0, stderr: '' },
      },
      advisoryMatches: [],
    });

    render(<MemoryRouter><EngineAddForm /></MemoryRouter>);
    await screen.findByRole('option', { name: /web-tier-host/ });
    await fillForm();
    await userEvent.click(screen.getByTestId('save-button'));
    expect(await screen.findByTestId('modal-confirm')).toBeDisabled();
  });
});
