package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.CompanyClosingDefaultsRequest;
import com.erp.erp_cloud.dto.CompanyResponseDTO;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.enums.AccountClass;
import com.erp.erp_cloud.enums.ChartTemplateType;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.repository.CompanyRepository;
import com.erp.erp_cloud.security.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit tests for CompanyService -- no Spring context, no database.
 * Verifies the current tenant's Company (bound via TenantContext.setContext,
 * exactly as TenantFilter does for every authenticated request) maps
 * correctly to CompanyResponseDTO, including the nullable chartTemplate.
 */
class CompanyServiceTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long GAIN_ACCOUNT_ID = 10L;
    private static final Long LOSS_ACCOUNT_ID = 11L;

    private CompanyRepository companyRepository;
    private ChartOfAccountsRepository accountRepository;
    private CompanyService service;

    @BeforeEach
    void setUp() {
        // getCurrentCompany() (the only method most of these tests
        // exercise) never touches either repository -- they only matter
        // for updateClosingDefaults(), added 2026-10-03 -- so plain mocks
        // with no stubbing are enough to satisfy the constructor here.
        // updateClosingDefaults() tests below stub them as needed.
        companyRepository = mock(CompanyRepository.class);
        accountRepository = mock(ChartOfAccountsRepository.class);
        service = new CompanyService(companyRepository, accountRepository);
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

    // ============================================================
    // updateClosingDefaults() -- NEW (2026-10-06): the company-level
    // Ganancia/Pérdida suggestion must obey the same "can't be the same
    // account twice" rule closeYear() already enforces for the accounts
    // actually confirmed for a specific year. Catching it here too means
    // a bad suggestion never gets saved in the first place, instead of
    // only failing later when someone tries to close a year with it.
    // ============================================================

    @Test
    @DisplayName("updateClosingDefaults() rejects the same account for both Ganancia and Pérdida")
    void updateClosingDefaults_sameAccountForBoth_throws() {
        Company company = new Company();
        company.setId(COMPANY_ID);
        TenantContext.setContext(company);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        // Same account id requested for both Ganancia and Pérdida below --
        // one stub covers both resolveEquityAccountOrNull() calls.
        when(accountRepository.findById(GAIN_ACCOUNT_ID))
                .thenReturn(Optional.of(equityAccount(GAIN_ACCOUNT_ID, "360501")));

        CompanyClosingDefaultsRequest request = new CompanyClosingDefaultsRequest();
        request.setDefaultGainAccountId(GAIN_ACCOUNT_ID);
        request.setDefaultLossAccountId(GAIN_ACCOUNT_ID);

        assertThatThrownBy(() -> service.updateClosingDefaults(request))
                .isInstanceOf(InvalidOperationException.class)
                .satisfies(ex -> assertThat(((InvalidOperationException) ex).getErrorCode())
                        .isEqualTo("GAIN_LOSS_ACCOUNTS_MUST_DIFFER"));

        verify(companyRepository, never()).save(any(Company.class));
    }

    @Test
    @DisplayName("updateClosingDefaults() accepts two different accounts for Ganancia and Pérdida")
    void updateClosingDefaults_differentAccounts_saves() {
        Company company = new Company();
        company.setId(COMPANY_ID);
        TenantContext.setContext(company);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(accountRepository.findById(GAIN_ACCOUNT_ID))
                .thenReturn(Optional.of(equityAccount(GAIN_ACCOUNT_ID, "360501")));
        when(accountRepository.findById(LOSS_ACCOUNT_ID))
                .thenReturn(Optional.of(equityAccount(LOSS_ACCOUNT_ID, "360101")));
        when(companyRepository.save(any(Company.class))).thenReturn(company);

        CompanyClosingDefaultsRequest request = new CompanyClosingDefaultsRequest();
        request.setDefaultGainAccountId(GAIN_ACCOUNT_ID);
        request.setDefaultLossAccountId(LOSS_ACCOUNT_ID);

        CompanyResponseDTO result = service.updateClosingDefaults(request);

        assertThat(result.getId()).isEqualTo(COMPANY_ID);
        verify(companyRepository).save(any(Company.class));
    }

    @Test
    @DisplayName("updateClosingDefaults() allows clearing both suggestions (both null) without tripping the equality check")
    void updateClosingDefaults_bothNull_clearsWithoutError() {
        Company company = new Company();
        company.setId(COMPANY_ID);
        TenantContext.setContext(company);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(companyRepository.save(any(Company.class))).thenReturn(company);

        CompanyClosingDefaultsRequest request = new CompanyClosingDefaultsRequest();
        request.setDefaultGainAccountId(null);
        request.setDefaultLossAccountId(null);

        CompanyResponseDTO result = service.updateClosingDefaults(request);

        assertThat(result.getId()).isEqualTo(COMPANY_ID);
        verify(companyRepository).save(any(Company.class));
    }

    private ChartOfAccounts equityAccount(Long id, String code) {
        ChartOfAccounts a = new ChartOfAccounts();
        a.setId(id);
        a.setCode(code);
        a.setName("Cuenta " + code);
        Company company = new Company();
        company.setId(COMPANY_ID);
        a.setCompany(company);
        a.setAccountClass(AccountClass.EQUITY);
        a.setPostingAccount(true);
        a.setRequiresThirdParty(false);
        a.setRequiresCostCenter(false);
        return a;
    }
}
