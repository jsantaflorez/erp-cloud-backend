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

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String tenantId = request.getHeader("X-Tenant-Id");

        if (tenantId == null || tenantId.isBlank()) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Missing X-Tenant-Id header"
            );
            return;
        }

        try {
            // 🔑 AQUÍ se define la company actual
            companyContext.setCurrentCompany(tenantId);

            filterChain.doFilter(request, response);

        } finally {
            // 🧹 Limpieza obligatoria (ThreadLocal)
            companyContext.clear();
        }
    }
}
