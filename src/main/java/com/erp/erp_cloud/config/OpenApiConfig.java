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
 * Defines the API documentation metadata and global security requirements
 * for the Multitenant ERP Cloud environment.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        // Name used to link the security scheme with the security requirement
        final String securitySchemeName = "TenantHeader";

        return new OpenAPI()
                /* * API Metadata: Title, version, and general description
                 */
                .info(new Info()
                        .title("ERP-CLOUD API")
                        .version("1.0")
                        .description("Enterprise Resource Planning - Cloud Accounting Module")
                        .contact(new Contact()
                                .name("ERP Cloud Development Team")
                                .email("dev-support@erpcloud.com")))

                /* * Components definition: This is where we define the security mechanism.
                 * We are using an API Key located in the Header (X-Tenant-Id).
                 */
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-Tenant-Id")
                                        .description("Custom header required to identify the Company/Tenant context.")))

                /* * Global Security Requirement:
                 * Using addSecurityItem() to enforce the use of the Tenant Header
                 * across all API endpoints in the Swagger UI.
                 */
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName));
    }
}