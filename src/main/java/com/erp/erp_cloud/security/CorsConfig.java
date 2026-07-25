package com.erp.erp_cloud.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    // Comma-separated origins in application.yml:
    // app.security.cors.allowed-origins: http://localhost:5173,https://app.erpcloud.com
    @Value("${app.security.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 1. Inject allowed origins from environment-specific properties file
        configuration.setAllowedOrigins(allowedOrigins);

        // 2. Explicit HTTP methods required for full ERP CRUD operations
        configuration.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        // 3. Standard headers + custom infrastructure headers

        configuration.setAllowedHeaders(List.of(
                "Authorization",       // JWT Bearer token
                "Content-Type",        // JSON payloads
                "Accept",
                "X-Requested-With",
                "X-Correlation-ID",    // Distributed tracing — consumed by GlobalExceptionHandler
                "X-Tenant-Id"          // Dynamic multi-tenancy bound routing header
        ));

        // 4. Expose headers so React Axios/Fetch can read them from the response
        configuration.setExposedHeaders(List.of(
                "Authorization",
                "X-Correlation-ID",
                "X-Tenant-Id"
        ));

        // 5. Required for Authorization headers and credentialed requests
        configuration.setAllowCredentials(true);

        // 6. Cache preflight OPTIONS response for 1 hour — reduces per-request latency
        configuration.setMaxAge(3600L);

        // 7. Apply this configuration globally to all API endpoints
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}