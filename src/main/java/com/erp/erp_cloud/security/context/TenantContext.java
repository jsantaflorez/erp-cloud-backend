package com.erp.erp_cloud.security.context;

import com.erp.erp_cloud.entity.Company;
import org.springframework.stereotype.Component;

/**
 * ThreadLocal-based tenant context holder.
 *
 * Dual-mode design:
 * - TenantFilter uses setCurrentTenant(Long) — zero DB queries, pure JWT claim binding.
 * - Existing accounting services use getCurrentCompany() — entity available when set via setContext(Company).
 */
@Component
public class TenantContext {

    private static final ThreadLocal<Company> CURRENT_COMPANY   = new ThreadLocal<>();
    private static final ThreadLocal<Long>    CURRENT_TENANT_ID = new ThreadLocal<>();

    /**
     * Binds only the companyId to the current thread.
     * Used by TenantFilter — no database query required.
     * getCurrentCompany() will throw if called after this method,
     * since the Company entity is not loaded.
     */
    public void setCurrentTenant(Long companyId) {
        CURRENT_TENANT_ID.set(companyId);
    }

    /**
     * Binds both the Company entity and its ID to the current thread.
     * Used by flows that have already loaded the Company from the database
     * and need to make it available to accounting services downstream.
     */
    public void setContext(Company company) {
        CURRENT_COMPANY.set(company);
        CURRENT_TENANT_ID.set(company.getId());
    }

    /**
     * Returns the Company entity bound to the current thread.
     * Only available if setContext(Company) was called upstream.
     * Throws if only setCurrentTenant(Long) was used.
     */
    public Company getCurrentCompany() {
        Company company = CURRENT_COMPANY.get();
        if (company == null) {
            throw new IllegalStateException(
                    "TENANT_CONTEXT | No Company entity bound to current thread. " +
                            "Use setContext(Company) if the entity is required downstream.");
        }
        return company;
    }

    /**
     * Returns the companyId bound to the current thread.
     * Available regardless of which setter was used.
     */
    public Long getCurrentTenant() {
        Long tenantId = CURRENT_TENANT_ID.get();
        if (tenantId == null) {
            throw new IllegalStateException(
                    "TENANT_CONTEXT | No tenant ID bound to current thread — " +
                            "ensure TenantFilter is active for this request path.");
        }
        return tenantId;
    }

    /**
     * Clears both ThreadLocals.
     * Must be called in the finally block of TenantFilter to prevent memory leaks
     * in thread-pooled servlet containers.
     */
    public void clear() {
        CURRENT_COMPANY.remove();
        CURRENT_TENANT_ID.remove();
    }
}