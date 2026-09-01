package com.BPL_Order_Engine_Admin.manager.auth.dto;

import java.time.Instant;

/**
 * Login response (also returned by {@code POST /api/auth/change-password}).
 *
 * <p>The frontend's {@code AuthContext} stores the {@code token} in
 * {@code localStorage} and replaces the user object on a successful
 * change-password.
 */
public record LoginResponse(
    String token,
    Instant expiresAt,
    UserSummary user,
    boolean mustChangePassword
) {}
