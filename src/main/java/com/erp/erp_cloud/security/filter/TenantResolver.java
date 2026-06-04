// TenantResolver.java
package com.erp.erp_cloud.security.filter;

import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantResolver {

    private final CompanyRepository companyRepository;

    /**
     * Resolves the Company entity from the database within a read-only transaction.
     * Called once per request by TenantFilter to populate the TenantContext.
     *
     * TODO (PERFORMANCE): Add @Cacheable("companies") when Redis is available
     * to eliminate per-request DB queries for a small, stable dataset.
     */
    @Transactional(readOnly = true)
    public Company resolve(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalStateException(
                        "TENANT_RESOLVER | Company not found for ID: " + companyId));
    }
}