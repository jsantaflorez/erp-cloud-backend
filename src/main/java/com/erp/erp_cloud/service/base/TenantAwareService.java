package com.erp.erp_cloud.service.base;

import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.security.context.TenantContext;

/**
 * Base class for all tenant-aware services.
 * Provides a single access point for the current Company context via TenantContext.
 * Eliminates boilerplate context calls across all accounting services.
 */
public abstract class TenantAwareService {

    protected Long currentTenantId() {
        return TenantContext.getCurrentTenant();
    }

    protected Company currentCompany() {
        return TenantContext.getCurrentCompany();
    }
}