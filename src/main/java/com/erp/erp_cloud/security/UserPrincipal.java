package com.erp.erp_cloud.security;

import com.erp.erp_cloud.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;


import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final String fullName;
    private final Long companyId;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean active;

    /**
     * Production constructor: Maps your database User entity metrics into Spring Security context properties.
     */
    public UserPrincipal(User user, Long companyId, Set<String> roleCodes, Set<String> permissionCodes) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.password = user.getPasswordHash(); // Binds perfectly to your entity's passwordHash field
        this.fullName = user.getFullName();     // Resolves the formatted firstName + lastName dynamic string
        this.companyId = companyId;
        this.active = user.isActive();

        // Unifies roles (with ROLE_ prefix) and granular permissions into a single collection
        this.authorities = Stream.concat(
                roleCodes.stream().map(role -> new SimpleGrantedAuthority(role)),
                permissionCodes.stream().map(SimpleGrantedAuthority::new)
        ).collect(Collectors.toSet());
    }

    /**
     * Overloaded testing/mock constructor: Directly injects flat parameters to support staging scenarios.
     */
    public UserPrincipal(Long id, String email, String password, String fullName, Long companyId, Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.companyId = companyId;
        this.authorities = authorities;
        this.active = true;
    }
    /**
     * Static factory method utilized by the JWT verification filter to rebuild
     * the stateless principal context directly from custom typed token claims.
     */

    /**
     * Static factory method utilized by the JWT verification filter to rebuild
     * the stateless principal context directly from custom typed token claims.
     */
    public static UserPrincipal fromClaims(UserPrincipalClaims claims, java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> authorities) {
        // Maps perfectly to your Java Record syntax and properties
        return new UserPrincipal(
                claims.userId(),    // Matches 'userId' field precisely
                claims.email(),     // Matches 'email' field precisely
                "",                 // Password is left blank as it is not needed for stateless requests
                null,               // fullName is not present in the token claims, defaults safely to null
                claims.companyId(), // Matches 'companyId' field precisely
                authorities
        );
    }
    // --- Custom Getters for downstream Enterprise Resource Planning context mappings ---

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getFullName() {
        return fullName;
    }

    // --- Spring Security UserDetails Contract Methods ---

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
        return email; // Your email field acts as the primary login security identity token identifier
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
        return active; // Binds to your entity's active execution boolean property state
    }
}