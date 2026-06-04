// TenantFilter.java
package com.erp.erp_cloud.security.filter;

import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.security.UserPrincipal;
import com.erp.erp_cloud.security.context.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    private final TenantContext tenantContext;
    private final TenantResolver tenantResolver;
    private final ObjectMapper objectMapper;

    /**
     * Bypasses tenant resolution for public endpoints and preflight requests.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || path.startsWith("/api/auth/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/swagger-resources")
                || path.startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        Long companyId = null;

        // PRIMARY: Extract companyId from the cryptographically signed JWT principal
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.getPrincipal() instanceof UserPrincipal principal) {
            companyId = principal.getCompanyId();

        } else {
            // FALLBACK: X-Tenant-Id header — valid only for unauthenticated public pipelines
            String headerTenant = request.getHeader("X-Tenant-Id");
            if (headerTenant != null && !headerTenant.isBlank()) {
                try {
                    companyId = Long.parseLong(headerTenant);
                } catch (NumberFormatException ex) {
                    log.warn("TENANT_FILTER_BLOCKED | reason: INVALID_TENANT_HEADER_FORMAT | URI: {} {}",
                            request.getMethod(), request.getRequestURI());
                    writeErrorResponse(response, "Invalid X-Tenant-Id header format.");
                    return;
                }
            }
        }

        // STRICT VALIDATION: All business endpoints require a resolved tenant context
        if (companyId == null) {
            log.warn("TENANT_FILTER_BLOCKED | reason: MISSING_TENANT_CONTEXT | URI: {} {}",
                    request.getMethod(), request.getRequestURI());
            writeErrorResponse(response, "Missing multi-tenant context — JWT claim or X-Tenant-Id header required.");
            return;
        }

        try {
            // Single DB query per request — wrapped in @Transactional(readOnly = true)
            Company company = tenantResolver.resolve(companyId);

            // Binds both Company entity and companyId to the current thread
            tenantContext.setContext(company);

            log.debug("TENANT_FILTER_BOUND | tenant: {} | URI: {} {}",
                    companyId, request.getMethod(), request.getRequestURI());

            filterChain.doFilter(request, response);

        } catch (IllegalStateException ex) {
            log.warn("TENANT_FILTER_BLOCKED | reason: COMPANY_NOT_FOUND | companyId: {}", companyId);
            writeErrorResponse(response, "Invalid tenant identifier.");

        } finally {
            // Always clear both ThreadLocals — prevents context leaking in thread pools
            tenantContext.clear();
        }
    }

    /**
     * Writes a structured ApiResponse error JSON directly to the servlet response.
     * Used when the filter intercepts before GlobalExceptionHandler is reachable.
     */
    private void writeErrorResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                objectMapper.writeValueAsString(ApiResponse.error(message))
        );
    }
}