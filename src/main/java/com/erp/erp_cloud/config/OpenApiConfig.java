package com.erp.erp_cloud.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for SpringDoc OpenAPI (Swagger).
 * Defines the API documentation metadata and global dual-layered security requirements
 * for the Multitenant ERP Cloud environment.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        // Shared security reference identifiers to map schemes to the global security context
        final String tokenSchemeName = "BearerAuth";
        final String tenantSchemeName = "TenantHeader";

        return new OpenAPI()
                /*
                 * API Metadata: Title, version, and general operational description
                 */
                .info(new Info()
                        .title("ERP-CLOUD API")
                        .version("1.0")
                        .description("Enterprise Resource Planning - Cloud Accounting Module")
                        .contact(new Contact()
                                .name("ERP Cloud Development Team")
                                .email("dev-support@erpcloud.com")))

                /*
                 * Components definition: Registers the concrete security mechanisms.
                 * Layer 1: Stateless Bearer JWT Authentication Scheme.
                 * Layer 2: API Key Header required to route and filter multi-tenant context.
                 */
                .components(new Components()
                        // 1. Setup the HTTP Bearer Authentication Scheme for JWT validation
                        .addSecuritySchemes(tokenSchemeName, new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Provide the raw Bearer JWT token returned by the login endpoint (Exclude the 'Bearer ' prefix)."))

                        // 2. Setup the custom ApiKey Header Scheme to manage tenant identification isolation
                        .addSecuritySchemes(tenantSchemeName, new SecurityScheme()
                                .name("X-Tenant-Id")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Custom header required to identify the target Company/Tenant context boundary.")))

                /*
                 * Global Security Requirement:
                 * Enforces BOTH the stateless Bearer Token and the context-scoped Tenant Header
                 * across all API endpoints exposed within the Swagger UI environment.
                 */
                .addSecurityItem(new SecurityRequirement()
                        .addList(tokenSchemeName)
                        .addList(tenantSchemeName));
    }
}