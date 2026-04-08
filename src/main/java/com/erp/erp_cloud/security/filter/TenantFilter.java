package com.erp.erp_cloud.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;



import com.erp.erp_cloud.security.context.CompanyContext;
@Component
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    private final CompanyContext companyContext;

    // 1. Define the whitelist for Swagger and static resources
    private static final String[] SWAGGER_WHITELIST = {
            "/v3/api-docs",
            "/swagger-ui",
            "/swagger-resources",
            "/configuration/ui",
            "/configuration/security"
    };

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // 2. BYPASS: If the request is for Swagger or OPTIONS, let it pass without validation
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || isSwaggerPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String tenantId = request.getHeader("X-Tenant-Id");

        // 3. Strict validation for all other business endpoints
        if (tenantId == null || tenantId.isBlank()) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Missing X-Tenant-Id header"
            );
            return;
        }

        try {
            companyContext.setCurrentCompany(tenantId);
            filterChain.doFilter(request, response);
        } finally {
            companyContext.clear();
        }
    }

    /**
     * Helper method to check if the current request is for Swagger documentation.
     */
    private boolean isSwaggerPath(String path) {
        for (String whitePath : SWAGGER_WHITELIST) {
            if (path.startsWith(whitePath)) {
                return true;
            }
        }
        return false;
    }
}