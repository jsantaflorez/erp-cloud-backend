package com.erp.erp_cloud.config;

import com.erp.erp_cloud.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bloque 0.1 (auditoria 2026-11-19, F1-02): created_by/updated_by must carry
 * the authenticated user's id, not a hardcoded 1.
 */
class JpaConfigTest {

    private final AuditorAware<Long> auditor = new JpaConfig().jpaAuditorProvider();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Returns the id of the authenticated user")
    void returnsAuthenticatedUserId() {
        UserPrincipal principal = new UserPrincipal(42L, "auxiliar@firma.com", "x", "Auxiliar", 4L, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        assertThat(auditor.getCurrentAuditor()).contains(42L);
    }

    @Test
    @DisplayName("Returns empty (column stays NULL) when nobody is authenticated -- never user 1")
    void emptyWithoutAuthentication() {
        assertThat(auditor.getCurrentAuditor()).isEmpty();
    }
}
