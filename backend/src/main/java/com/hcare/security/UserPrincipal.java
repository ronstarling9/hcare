package com.hcare.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class UserPrincipal implements UserDetails {

    private final UUID userId;
    private final UUID agencyId;
    private final String role;
    /** Non-null only when role == FAMILY_PORTAL. Hard scope boundary for /family/portal/** endpoints. */
    private final UUID clientId;

    /** Constructor for admin/scheduler tokens (clientId is null). */
    public UserPrincipal(UUID userId, UUID agencyId, String role) {
        this(userId, agencyId, role, null);
    }

    /** Constructor for FAMILY_PORTAL tokens (clientId is non-null). */
    public UserPrincipal(UUID userId, UUID agencyId, String role, UUID clientId) {
        this.userId = userId;
        this.agencyId = agencyId;
        this.role = role;
        this.clientId = clientId;
    }

    public UUID getUserId() { return userId; }
    public UUID getAgencyId() { return agencyId; }
    public String getRole() { return role; }
    /** Returns the clientId scope for FAMILY_PORTAL tokens; null for admin/scheduler tokens. */
    public UUID getClientId() { return clientId; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override public String getPassword() { return null; }
    @Override public String getUsername() { return userId.toString(); }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
