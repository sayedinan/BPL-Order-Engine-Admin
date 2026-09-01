/**
 * RoleBadge — pill that surfaces the current role in the header.
 *
 * Three roles in v0.3: SYS_ADMIN (purple), ADMIN (blue), USER (gray).
 * The a11y label is the full role name so screen-readers don't have
 * to parse the abbreviation.
 */
import type { Role } from '../api/types';

interface RoleBadgeProps {
  role: Role;
}

const LABEL: Record<Role, string> = {
  SYS_ADMIN: 'Sys.Admin',
  ADMIN: 'Admin',
  USER: 'User',
};

const CLASS: Record<Role, string> = {
  SYS_ADMIN: 'role-badge role-badge--sysadmin',
  ADMIN: 'role-badge role-badge--admin',
  USER: 'role-badge role-badge--user',
};

export function RoleBadge({ role }: RoleBadgeProps) {
  return (
    <span
      className={CLASS[role]}
      aria-label={`Signed in as ${role}`}
      title={`Current role: ${role}`}
    >
      <span className="role-badge__dot" aria-hidden="true" />
      {LABEL[role]}
    </span>
  );
}
