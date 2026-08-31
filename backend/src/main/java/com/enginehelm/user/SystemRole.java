package com.enginehelm.user;

/**
 * System Role (SPEC §3.1). Ordered: {@link #SYS_ADMIN} > {@link #ADMIN}
 * > {@link #STANDARD}.
 *
 * <p>The persisted form is the SPEC-compliant label
 * ({@code sys.admin} / {@code admin} / {@code standard}). Authority
 * granted to Spring Security uses the {@code ROLE_} prefix with
 * upper-snake form ({@code ROLE_SYS_ADMIN}, etc.) so the standard
 * {@code hasRole(...)} checks line up.
 */
public enum SystemRole {
    SYS_ADMIN("sys.admin", "ROLE_SYS_ADMIN"),
    ADMIN("admin", "ROLE_ADMIN"),
    STANDARD("standard", "ROLE_STANDARD");

    private final String persisted;
    private final String authority;

    SystemRole(String persisted, String authority) {
        this.persisted = persisted;
        this.authority = authority;
    }

    public String persisted() {
        return persisted;
    }

    public String authority() {
        return authority;
    }

    public static SystemRole fromPersisted(String value) {
        for (SystemRole r : values()) {
            if (r.persisted.equals(value)) {
                return r;
            }
        }
        throw new IllegalArgumentException("unknown system role: " + value);
    }
}
