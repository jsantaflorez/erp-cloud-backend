package com.erp.erp_cloud.security;

import org.springframework.beans.factory.annotation.Autowired;
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
 * ROLE_ prefix). Default: SYS_ADMIN,COMPANY_ADMIN.
 *
 * NEW (2026-11-19, 3 perfiles): {@code app.security.writer-roles} lists the
 * role codes that may WRITE (any POST/PUT/PATCH/DELETE under /api/v1/**,
 * enforced centrally in SecurityConfig). Default:
 * SYS_ADMIN,COMPANY_ADMIN,ACCOUNTANT. It is an allow-list on purpose: any
 * other role -- VIEWER ("Consulta") or a custom role nobody classified --
 * is read-only by default, instead of writing by default.
 *
 * The three profiles, by role code:
 *   COMPANY_ADMIN / SYS_ADMIN  Administrador: everything.
 *   ACCOUNTANT                 Auxiliar/Contador: writes, but no annul,
 *                              no edit of a saved entry, no period close,
 *                              no user admin (those are @authz.isAdmin()).
 *   VIEWER                     Consulta: read-only (GET only).
 */
@Component("authz")
public class AuthorizationRules {

    static final List<String> DEFAULT_WRITER_ROLES = List.of("SYS_ADMIN", "COMPANY_ADMIN", "ACCOUNTANT");

    private final Set<String> adminAuthorities;
    private final Set<String> writerAuthorities;

    /** Admin list only; writers = the default writer list. Kept for tests. */
    public AuthorizationRules(List<String> adminRoles) {
        this(adminRoles, DEFAULT_WRITER_ROLES);
    }

    @Autowired
    public AuthorizationRules(
            @Value("${app.security.admin-roles:SYS_ADMIN,COMPANY_ADMIN}") List<String> adminRoles,
            @Value("${app.security.writer-roles:SYS_ADMIN,COMPANY_ADMIN,ACCOUNTANT}") List<String> writerRoles) {
        this.adminAuthorities = toAuthorities(adminRoles);
        this.writerAuthorities = toAuthorities(writerRoles);
    }

    private static Set<String> toAuthorities(List<String> roleCodes) {
        return roleCodes.stream()
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

    /**
     * True when the current user may write (create, change, import...) in
     * the active company: an administrator, or a holder of one of the
     * configured writer roles. Read-only profiles (VIEWER) return false.
     */
    public boolean canWrite() {
        return canWrite(SecurityContextHolder.getContext().getAuthentication());
    }

    public boolean canWrite(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> adminAuthorities.contains(a) || writerAuthorities.contains(a));
    }

    Set<String> adminAuthorities() {
        return adminAuthorities;
    }

    Set<String> writerAuthorities() {
        return writerAuthorities;
    }
}
