package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.ChartOfAccountRequest;
import com.erp.erp_cloud.dto.ChartOfAccountResponseDTO;
import com.erp.erp_cloud.dto.SiewinPucImportRequest;
import com.erp.erp_cloud.dto.SiewinPucImportResultDTO;
import com.erp.erp_cloud.dto.SiewinPucRowDTO;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.repository.CompanyRepository;
import com.erp.erp_cloud.security.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests (no Spring, no DB) for SiewinPucImportService, focused on the
 * ADD_MISSING mode added 2026-11-17: re-uploading the current SIEWIN PUC on
 * a company that already has a plan creates only the missing codes and
 * never touches existing accounts.
 */
class SiewinPucImportServiceTest {

    private static final Long COMPANY_ID = 1L;

    @Mock private ChartOfAccountsRepository chartOfAccountsRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private ChartOfAccountService chartOfAccountService;

    private SiewinPucImportService service;
    private final AtomicLong nextId = new AtomicLong(9000L);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new SiewinPucImportService(chartOfAccountsRepository, companyRepository, chartOfAccountService);

        Company company = new Company();
        company.setId(COMPANY_ID);
        TenantContext.setContext(company);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(chartOfAccountService.create(any(ChartOfAccountRequest.class))).thenAnswer(inv -> {
            ChartOfAccountResponseDTO dto = new ChartOfAccountResponseDTO();
            dto.setId(nextId.getAndIncrement());
            return dto;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // nivel1..nivel3, nombre, imputable, bare, ceco
    private SiewinPucRowDTO row(int n1, int n2, int n3, String name, int posting) {
        return new SiewinPucRowDTO(n1, n2, n3, 0, 0, 0, name, posting, 0, 0);
    }

    private ChartOfAccounts existing(long id, String code, String name, boolean posting, boolean active) {
        ChartOfAccounts a = new ChartOfAccounts();
        a.setId(id);
        a.setCode(code);
        a.setName(name);
        a.setPostingAccount(posting);
        a.setActive(active);
        return a;
    }

    /** PUC actual de SIEWIN: 11 Efectivo > 1105 Caja > 110505 Caja general (+ nueva 110510 Caja menor). */
    private List<SiewinPucRowDTO> currentSiewinPuc() {
        return List.of(
                row(11, 0, 0, "Efectivo", 0),
                row(11, 5, 0, "Caja", 0),
                row(11, 5, 5, "Caja general", 1),
                row(11, 5, 10, "Caja menor", 1));
    }

    @Test
    @DisplayName("Full import is still rejected when the plan already has accounts")
    void fullImport_nonEmptyPlan_throws() {
        when(chartOfAccountsRepository.existsByCompanyId(COMPANY_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.importFromSiewin(new SiewinPucImportRequest(currentSiewinPuc())))
                .isInstanceOfSatisfying(InvalidOperationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("CHART_OF_ACCOUNTS_NOT_EMPTY"));
        verify(chartOfAccountService, never()).create(any());
    }

    @Test
    @DisplayName("ADD_MISSING creates only the codes that don't exist, under their existing parent")
    void addMissing_createsOnlyNewCodes() {
        when(chartOfAccountsRepository.existsByCompanyId(COMPANY_ID)).thenReturn(true);
        when(chartOfAccountsRepository.findByCompanyIdOrderByCodeAsc(COMPANY_ID)).thenReturn(new ArrayList<>(List.of(
                existing(1L, "1", "Activo", false, true),
                existing(11L, "11", "Efectivo", false, true),
                existing(1105L, "1105", "Caja", false, true),
                existing(110505L, "110505", "Caja general", true, true))));

        SiewinPucImportResultDTO result = service.importFromSiewin(
                new SiewinPucImportRequest(currentSiewinPuc(), SiewinPucImportRequest.MODE_ADD_MISSING));

        ArgumentCaptor<ChartOfAccountRequest> captor = ArgumentCaptor.forClass(ChartOfAccountRequest.class);
        verify(chartOfAccountService, times(1)).create(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("110510");
        assertThat(captor.getValue().getParentId()).isEqualTo(1105L);
        assertThat(result.accountsCreated()).isEqualTo(1);
        assertThat(result.rootAccountsCreated()).isZero();
        assertThat(result.accountsAlreadyExisting()).isEqualTo(3);
    }

    @Test
    @DisplayName("ADD_MISSING keeps an inactive existing code as-is (never re-created)")
    void addMissing_inactiveExistingCode_notRecreated() {
        when(chartOfAccountsRepository.existsByCompanyId(COMPANY_ID)).thenReturn(true);
        when(chartOfAccountsRepository.findByCompanyIdOrderByCodeAsc(COMPANY_ID)).thenReturn(new ArrayList<>(List.of(
                existing(1L, "1", "Activo", false, true),
                existing(11L, "11", "Efectivo", false, true),
                existing(1105L, "1105", "Caja", false, true),
                existing(110505L, "110505", "Caja general", true, false),
                existing(110510L, "110510", "Caja menor", true, true))));

        SiewinPucImportResultDTO result = service.importFromSiewin(
                new SiewinPucImportRequest(currentSiewinPuc(), SiewinPucImportRequest.MODE_ADD_MISSING));

        verify(chartOfAccountService, never()).create(any());
        assertThat(result.accountsCreated()).isZero();
        assertThat(result.accountsAlreadyExisting()).isEqualTo(4);
    }

    @Test
    @DisplayName("ADD_MISSING reports, before creating anything, a new code whose existing parent is a posting account")
    @SuppressWarnings("unchecked")
    void addMissing_parentIsPostingInPlan_throws() {
        when(chartOfAccountsRepository.existsByCompanyId(COMPANY_ID)).thenReturn(true);
        // En el ERP la 1105 quedó como cuenta de movimiento; en SIEWIN ahora tiene subcuentas.
        when(chartOfAccountsRepository.findByCompanyIdOrderByCodeAsc(COMPANY_ID)).thenReturn(new ArrayList<>(List.of(
                existing(1L, "1", "Activo", false, true),
                existing(11L, "11", "Efectivo", false, true),
                existing(1105L, "1105", "Caja", true, true))));

        assertThatThrownBy(() -> service.importFromSiewin(
                new SiewinPucImportRequest(currentSiewinPuc(), SiewinPucImportRequest.MODE_ADD_MISSING)))
                .isInstanceOfSatisfying(InvalidOperationException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo("SIEWIN_IMPORT_INTEGRITY_GAP");
                    Map<String, Object> details = (Map<String, Object>) ex.getDetails();
                    assertThat((List<String>) details.get("parentIsPostingInPlan")).hasSize(2);
                });
        verify(chartOfAccountService, never()).create(any());
    }

    @Test
    @DisplayName("ADD_MISSING reports a new code whose existing parent is inactive")
    @SuppressWarnings("unchecked")
    void addMissing_parentInactiveInPlan_throws() {
        when(chartOfAccountsRepository.existsByCompanyId(COMPANY_ID)).thenReturn(true);
        when(chartOfAccountsRepository.findByCompanyIdOrderByCodeAsc(COMPANY_ID)).thenReturn(new ArrayList<>(List.of(
                existing(1L, "1", "Activo", false, true),
                existing(11L, "11", "Efectivo", false, true),
                existing(1105L, "1105", "Caja", false, false),
                existing(110505L, "110505", "Caja general", true, true))));

        assertThatThrownBy(() -> service.importFromSiewin(
                new SiewinPucImportRequest(currentSiewinPuc(), SiewinPucImportRequest.MODE_ADD_MISSING)))
                .isInstanceOfSatisfying(InvalidOperationException.class, ex -> {
                    Map<String, Object> details = (Map<String, Object>) ex.getDetails();
                    assertThat((List<String>) details.get("parentInactiveInPlan")).hasSize(1);
                });
        verify(chartOfAccountService, never()).create(any());
    }
}
