package com.erp.erp_cloud.security.context;

import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CompanyContext {

    private final CompanyRepository companyRepository;

    private static final ThreadLocal<Company> CURRENT = new ThreadLocal<>();

    public void setCurrentCompany(String tenantId) {
        Company company = companyRepository
                .findByTenantId(tenantId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Company not found for tenant " + tenantId
                        )
                );

        CURRENT.set(company);
    }

    public Company getCurrentCompany() {
        Company company = CURRENT.get();
        if (company == null) {
            throw new IllegalStateException("No current company set");
        }
        return company;
    }

    public void clear() {
        CURRENT.remove();
    }
}
