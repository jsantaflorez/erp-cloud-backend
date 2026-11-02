package com.erp.erp_cloud.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit tests for the @PreAuthorize("@authz.isAdmin()") rule
 * (audit 2026-11-02, C1). No Spring context.
 */
class AuthorizationRulesTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateWith(String... authorities) {
        var granted = java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, granted));
    }

    @Test
    @DisplayName("Configured role codes are normalized with the ROLE_ prefix")
    void normalizesRoleCodes() {
        AuthorizationRules rules = new AuthorizationRules(List.of(" SYS_ADMIN ", "ROLE_ADMIN", ""));
        assertThat(rules.adminAuthorities()).containsExactlyInAnyOrder("ROLE_SYS_ADMIN", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("A user holding a configured admin role is an administrator")
    void adminRoleGrantsAccess() {
        authenticateWith("ROLE_SYS_ADMIN", "INVOICE_READ");
        assertThat(new AuthorizationRules(List.of("SYS_ADMIN", "ADMIN")).isAdmin()).isTrue();
    }

    @Test
    @DisplayName("A user with only non-admin roles or permissions is not an administrator")
    void nonAdminIsDenied() {
        authenticateWith("ROLE_ACCOUNTANT", "SYS_ADMIN");   // a PERMISSION named like a role does not count
        assertThat(new AuthorizationRules(List.of("SYS_ADMIN", "ADMIN")).isAdmin()).isFalse();
    }

    @Test
    @DisplayName("No authentication means no admin access")
    void anonymousIsDenied() {
        assertThat(new AuthorizationRules(List.of("SYS_ADMIN")).isAdmin()).isFalse();
    }
}
