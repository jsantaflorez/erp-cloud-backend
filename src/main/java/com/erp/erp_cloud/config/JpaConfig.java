package com.erp.erp_cloud.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
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
        /*
         * TODO: POST-AUTHENTICATION PHASE
         * Once the JWT filter is ready, we will update this lambda expression to fetch
         * the authenticated user principal from SecurityContextHolder.getContext().getAuthentication()
         */

        // FIXME: TEMPORARY BOOTSTRAP PHASE
        // Returns hardcoded User ID 1L (System Admin) to match our database BIGINT refactoring.
        return () -> Optional.of(1L);
    }
}