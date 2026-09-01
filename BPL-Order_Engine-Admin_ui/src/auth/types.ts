/**
 * Auth domain types. Shared by AuthContext, the API client, and the
 * Login screen so the role string and credential shape have one
 * source of truth.
 */

export type Role = 'ADMIN' | 'VIEWER';

/**
 * The two pre-seeded users in the backend's in-memory store
 * (see SecurityConfig in the backend). The quick-login buttons on
 * the Login screen pre-fill these values so the demo can be driven
 * with a single click.
 */
export interface DemoUser {
  username: string;
  password: string;
  role: Role;
}

export const DEMO_USERS: Record<Role, DemoUser> = {
  ADMIN: { username: 'admin', password: 'admin123', role: 'ADMIN' },
  VIEWER: { username: 'viewer', password: 'viewer123', role: 'VIEWER' },
};

/**
 * The auth state held in React context. We store the *base64-encoded*
 * Authorization header value (not the raw password) so the password
 * isn't sitting in plain text in the React tree, even though it has
 * to be there to make HTTP Basic work.
 */
export interface AuthState {
  username: string;
  role: Role;
  /** base64 of "username:password" — passed verbatim as the Bearer-equivalent Basic header. */
  authorizationHeader: string;
}
