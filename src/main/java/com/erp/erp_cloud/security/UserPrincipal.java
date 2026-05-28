package com.erp.erp_cloud.security;

import com.erp.erp_cloud.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final Long companyId;
    private final boolean active;
    private final LocalDateTime lockedUntil;
    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * Standard constructor used during the initial authentication/login process
     * when loading credentials from the database.
     */
    public UserPrincipal(User user, Long companyId, Set<String> roleCodes, Set<String> permissionCodes) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.password = user.getPasswordHash();
        this.companyId = companyId;
        this.active = user.isActive();
        this.lockedUntil = user.getLockedUntil();

        // Combines pure permissions (e.g., USER_CREATE) and roles prefixed with ROLE_ (e.g., ROLE_SYS_ADMIN)
        // to enable full flexibility using both hasAuthority() and hasRole() in security expressions.
        this.authorities = Stream.concat(
                permissionCodes.stream().map(SimpleGrantedAuthority::new),
                roleCodes.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role))
        ).collect(Collectors.toSet());
    }

    /**
     * Private constructor designed to support the stateless factory method instantiation pattern.
     */
    private UserPrincipal(Long id, String email, Long companyId, Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.password = null; // Omitted as the request has already been cryptographically validated via JWT
        this.companyId = companyId;
        this.active = true;
        this.lockedUntil = null;
        this.authorities = authorities;
    }

    /**
     * Factory method to rebuild a rich, typed UserPrincipal directly from trusted JWT claims.
     * This avoids redundant database hits or lazy loading initialization errors during API request evaluation.
     */
    public static UserPrincipal fromClaims(UserPrincipalClaims claims, Collection<? extends GrantedAuthority> authorities) {
        return new UserPrincipal(
                claims.userId(),
                claims.email(),
                claims.companyId(),
                authorities
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
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
    // Dynamic validation against the security control field for failed login attempts
    public boolean isAccountNonLocked() {
        return lockedUntil == null || lockedUntil.isBefore(LocalDateTime.now());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}