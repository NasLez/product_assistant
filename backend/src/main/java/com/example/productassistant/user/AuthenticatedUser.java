package com.example.productassistant.user;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.userdetails.UserDetails;

public final class AuthenticatedUser implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long id;
    private final String email;
    private String passwordHash;
    private final boolean enabled;

    public AuthenticatedUser(long id, String email, String passwordHash, boolean enabled) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
    }

    public static AuthenticatedUser from(AppUserEntity user) {
        return new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                Boolean.TRUE.equals(user.getEnabled()));
    }

    public long getId() { return id; }
    public String getEmail() { return email; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
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
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
