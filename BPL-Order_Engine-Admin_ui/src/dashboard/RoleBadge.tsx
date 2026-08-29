import type { Role } from '../auth/types';

interface RoleBadgeProps {
  role: Role;
}

/**
 * Pill that surfaces the current role in the header. Has its own
 * a11y label so screen-readers don't have to parse the abbreviation.
 */
export function RoleBadge({ role }: RoleBadgeProps) {
  const isAdmin = role === 'ADMIN';
  return (
    <span
      className={`role-badge ${isAdmin ? 'role-badge--admin' : 'role-badge--viewer'}`}
      aria-label={`Signed in as ${role}`}
      title={`Current role: ${role}`}
    >
      <span className="role-badge__dot" aria-hidden="true" />
      {isAdmin ? 'Admin' : 'Viewer'}
    </span>
  );
}
