package com.erp.erp_cloud.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Central authorization rules used from {@code @PreAuthorize} expressions
 * (audit 2026-11-02, finding C1), e.g. {@code @PreAuthorize("@authz.isAdmin()")}.
 *
 * Role codes are company-scoped rows in the database, so the list of codes
 * that count as "administrator" is configuration, not code:
 * {@code app.security.admin-roles} (comma separated, with or without the
 * ROLE_ prefix). Default: SYS_ADMIN,ADMIN.
 */
@Component("authz")
public class AuthorizationRules {

    private final Set<String> adminAuthorities;

    public AuthorizationRules(@Value("${app.security.admin-roles:SYS_ADMIN,ADMIN}") List<String> adminRoles) {
        this.adminAuthorities = adminRoles.stream()
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .map(code -> code.startsWith("ROLE_") ? code : "ROLE_" + code)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * True when the current user holds, in the ACTIVE company of its JWT,
     * at least one of the configured administrator roles. Roles are already
     * scoped to that company by CustomUserDetailsService at login/switch.
     */
    public boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(adminAuthorities::contains);
    }

    Set<String> adminAuthorities() {
        return adminAuthorities;
    }
}
