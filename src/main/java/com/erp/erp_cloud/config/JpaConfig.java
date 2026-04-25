package com.erp.erp_cloud.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import java.util.Optional;

@Configuration
/* * Enable JPA Auditing and link it to the auditorProvider bean.
 * This is what triggers the @CreatedBy and @LastModifiedBy annotations.
 */
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaConfig {

    /**
     * Component that provides the current user to the auditing infrastructure.
     * Currently returns "SYSTEM" as a fallback until JWT/Security is implemented.
     * * @return The username of the current actor.
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.of("SYSTEM");
        // Logic: Java Lambda that implements the getCurrentAuditor() method
    }
}