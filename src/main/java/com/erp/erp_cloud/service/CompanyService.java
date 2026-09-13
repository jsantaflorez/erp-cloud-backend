package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.CompanyResponseDTO;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.service.base.TenantAwareService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NEW (2026-09-10): first Company-facing service in the app -- there is no
 * Company CRUD yet (companies are provisioned directly in the database),
 * but the frontend needs a real way to read the current tenant's own data
 * instead of the hardcoded stand-in it uses today (see
 * erp-cloud-frontend/src/services/tenantSession.js). Started minimal:
 * just what's needed to know whether -- and which -- chart-of-accounts
 * template applies, so ChartOfAccountPage can decide whether to offer
 * name suggestions. Grows into a real Company settings endpoint later
 * without needing a different shape.
 */
@Service
@Transactional(readOnly = true)
public class CompanyService extends TenantAwareService {

    public CompanyResponseDTO getCurrentCompany() {
        Company company = currentCompany();
        return CompanyResponseDTO.builder()
                .id(company.getId())
                .legalName(company.getLegalName())
                .tradeName(company.getTradeName())
                .chartTemplate(company.getChartTemplate() != null ? company.getChartTemplate().name() : null)
                .build();
    }
}
