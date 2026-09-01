package com.BPL_Order_Engine_Admin.manager.auth;

import com.BPL_Order_Engine_Admin.manager.user.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * v0.3 {@code UserDetails} wrapper around the {@code User} entity.
 *
 * <p>Exposes the username, the BCrypt password hash, and the role as
 * a Spring Security authority (prefixed with {@code ROLE_} per
 * convention so {@code @PreAuthorize("hasRole('SYS_ADMIN')")} works).
 *
 * <p>Carries the user id and the {@code mustChangePassword} flag for
 * downstream consumers (controllers, JWT issuance).
 */
public class UserPrincipal implements UserDetails {

    @Getter
    private final User user;

    public UserPrincipal(User user) {
        this.user = user;
    }

    public UUID getId() {
        return user.getId();
    }

    public boolean isMustChangePassword() {
        return user.isMustChangePassword();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRoleType().name()));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
