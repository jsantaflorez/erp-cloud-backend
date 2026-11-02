// TenantFilter.java
package com.erp.erp_cloud.security.filter;

import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.repository.UserRoleRepository;
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

    // private final TenantContext tenantContext;
    private final TenantResolver tenantResolver;
    private final UserRoleRepository userRoleRepository;
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

        // SECURITY (audit 2026-11-02, M1): the tenant comes ONLY from the
        // signed JWT. The old X-Tenant-Id header fallback was removed: it was
        // harmless only as long as this filter happened to run after Spring
        // Security, and any future @Order change would have turned it into a
        // full cross-tenant bypass.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)
                || principal.getCompanyId() == null) {
            log.warn("TENANT_FILTER_BLOCKED | reason: MISSING_TENANT_CONTEXT | URI: {} {}",
                    request.getMethod(), request.getRequestURI());
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "MISSING_TENANT_CONTEXT");
            return;
        }
        Long companyId = principal.getCompanyId();

        // SECURITY (audit 2026-11-02, M2): re-check on every request that the
        // user is still active and still holds a role in an active company,
        // so revoking access takes effect immediately, not at token expiry.
        if (!userRoleRepository.hasActiveAccess(principal.getId(), companyId)) {
            log.warn("TENANT_FILTER_BLOCKED | reason: ACCESS_REVOKED | userId: {} | tenant: {} | URI: {} {}",
                    principal.getId(), companyId, request.getMethod(), request.getRequestURI());
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "ACCESS_REVOKED");
            return;
        }

        try {
            // Single DB query per request — wrapped in @Transactional(readOnly = true)
            Company company = tenantResolver.resolve(companyId);


            // Binds both Company entity and companyId to the current thread
            TenantContext.setContext(company);


            log.debug("TENANT_FILTER_BOUND | tenant: {} | URI: {} {}",
                    companyId, request.getMethod(), request.getRequestURI());

            filterChain.doFilter(request, response);

        } catch (IllegalStateException ex) {
            log.warn("TENANT_FILTER_BLOCKED | reason: COMPANY_NOT_FOUND | companyId: {}", companyId);
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "INVALID_TENANT");

        } finally {
            // Always clear both ThreadLocals — prevents context leaking in thread pools
            TenantContext.clear();
        }
    }

    /**
     * Writes a structured ApiResponse error JSON directly to the servlet response.
     * Used when the filter intercepts before GlobalExceptionHandler is reachable.
     */
    private void writeErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                objectMapper.writeValueAsString(ApiResponse.error(message))
        );
    }
}