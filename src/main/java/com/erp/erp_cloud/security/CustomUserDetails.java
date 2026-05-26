package com.erp.erp_cloud.security;



import com.erp.erp_cloud.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Adapter class that wraps our custom domain User entity
 * into Spring Security's standard UserDetails contract.
 */
public class CustomUserDetails implements UserDetails {

    private final User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    /**
     * Returns the authorities (roles/permissions) granted to the user.
     * For now, we return an empty list. We will populate this from UserRole
     * once we implement the multi-tenant context filters.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash(); // Maps to our secure BCrypt hash column
    }

    @Override
    public String getUsername() {
        return user.getEmail(); // In our ERP, the email acts as the unique username
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // Simple logic for now, can be expanded if needed
    }

    @Override
    public boolean isAccountNonLocked() {
        // If lockedUntil is set and is in the future, the account is locked
        if (user.getLockedUntil() != null) {
            return user.getLockedUntil().isBefore(java.time.LocalDateTime.now());
        }
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isActive(); // Tied directly to our custom 'active' boolean flag
    }

    /**
     * Helper method to easily extract the raw User entity ID when auditing or tracking sessions.
     */
    public Long getId() {
        return user.getId();
    }
}