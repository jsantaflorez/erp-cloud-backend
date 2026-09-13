package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.CompanyResponseDTO;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.enums.ChartTemplateType;
import com.erp.erp_cloud.security.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit tests for CompanyService -- no Spring context, no database.
 * Verifies the current tenant's Company (bound via TenantContext.setContext,
 * exactly as TenantFilter does for every authenticated request) maps
 * correctly to CompanyResponseDTO, including the nullable chartTemplate.
 */
class CompanyServiceTest {

    private static final Long COMPANY_ID = 1L;

    private CompanyService service;

    @BeforeEach
    void setUp() {
        service = new CompanyService();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("getCurrentCompany maps chartTemplate name when configured")
    void getCurrentCompany_withChartTemplate_mapsTemplateName() {
        Company company = new Company();
        company.setId(COMPANY_ID);
        company.setLegalName("Comercializadora Andina S.A.S.");
        company.setTradeName("Andina");
        company.setChartTemplate(ChartTemplateType.COMERCIAL);
        TenantContext.setContext(company);

        CompanyResponseDTO result = service.getCurrentCompany();

        assertThat(result.getId()).isEqualTo(COMPANY_ID);
        assertThat(result.getLegalName()).isEqualTo("Comercializadora Andina S.A.S.");
        assertThat(result.getTradeName()).isEqualTo("Andina");
        assertThat(result.getChartTemplate()).isEqualTo("COMERCIAL");
    }

    @Test
    @DisplayName("getCurrentCompany maps null chartTemplate as null, never as a literal string")
    void getCurrentCompany_withoutChartTemplate_mapsNull() {
        Company company = new Company();
        company.setId(COMPANY_ID);
        company.setLegalName("Cooperativa Multiactiva del Valle");
        company.setTradeName(null);
        company.setChartTemplate(null);
        TenantContext.setContext(company);

        CompanyResponseDTO result = service.getCurrentCompany();

        assertThat(result.getId()).isEqualTo(COMPANY_ID);
        assertThat(result.getLegalName()).isEqualTo("Cooperativa Multiactiva del Valle");
        assertThat(result.getChartTemplate()).isNull();
    }
}
