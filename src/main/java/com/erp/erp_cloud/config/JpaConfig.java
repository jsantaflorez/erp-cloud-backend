package com.erp.erp_cloud.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.erp.erp_cloud.security.UserPrincipal;
import java.util.Optional;

/**
 * Main JPA and Persistence configuration class for ERP Cloud.
 * Centralizes the auditing infrastructure and binds it to numeric User IDs.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "jpaAuditorProvider") // Links directly to the bean below
public class JpaConfig {

    /**
     * Core component that feeds the current acting user's ID to Spring Data JPA lifecycle events.
     * Populates @CreatedBy and @LastModifiedBy automatically.
     * * @return An AuditorAware implementation tracking user IDs as Long.
     */
    @Bean
    public AuditorAware<Long> jpaAuditorProvider() {
        // FIX (2026-11-19, auditoria F1-02 / Bloque 0.1): before this, the
        // provider returned a hardcoded 1L, so created_by/updated_by of
        // EVERY row in EVERY table said "user 1" no matter who acted. It now
        // returns the authenticated user's id (UserPrincipal, set by the JWT
        // filter on every request). With no authenticated user (system work
        // outside a request, failed-login bookkeeping on /api/auth/login) it
        // returns empty: Spring Data then leaves the column NULL instead of
        // attributing the change to someone who did not make it.
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null
                    && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof UserPrincipal principal
                    && principal.getId() != null) {
                return Optional.of(principal.getId());
            }
            return Optional.empty();
        };
    }
}