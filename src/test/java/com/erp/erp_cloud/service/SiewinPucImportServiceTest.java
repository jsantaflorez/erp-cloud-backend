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
        assertThat(result.createdAccounts()).singleElement().satisfies(a -> {
            assertThat(a.code()).isEqualTo("110510");
            assertThat(a.name()).isEqualTo("Caja menor");
            assertThat(a.postingAccount()).isTrue();
            assertThat(a.nature()).isEqualTo("D");
        });
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
        assertThat(result.createdAccounts()).isEmpty();
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
    // ---------------------------------------------------------------
    // "Varios" 9999 -> 99 (decisión del usuario 2026-11-17)
    // ---------------------------------------------------------------

    private SiewinPucRowDTO row4(int n1, int n2, int n3, int n4, String name, int posting) {
        return new SiewinPucRowDTO(n1, n2, n3, n4, 0, 0, name, posting, 0, 0);
    }

    @Test
    @DisplayName("A SIEWIN level of 9999 (\"Varios\") becomes slot 99 and is listed back")
    void fullImport_catchAll9999_mappedTo99() {
        when(chartOfAccountsRepository.existsByCompanyId(COMPANY_ID)).thenReturn(false);
        List<SiewinPucRowDTO> rows = List.of(
                row(22, 0, 0, "Proveedores", 0),
                row(22, 5, 0, "Nacionales", 0),
                row(22, 5, 5, "Proveedores nacionales", 0),
                row4(22, 5, 5, 14, "Cadena Asfaltos", 1),
                row4(22, 5, 5, 9999, "Proveedores varios", 1));

        SiewinPucImportResultDTO result = service.importFromSiewin(new SiewinPucImportRequest(rows));

        assertThat(result.createdAccounts()).extracting(SiewinPucImportResultDTO.CreatedAccount::code)
                .contains("22050514", "22050599");
        assertThat(result.catchAllRemapped()).containsExactly("22-05-05-9999 -> 22050599 - Proveedores varios");
    }

    @Test
    @DisplayName("9999 colliding with a real 99 sibling in the file is reported and nothing is created")
    @SuppressWarnings("unchecked")
    void fullImport_catchAllCollidesWithReal99_throws() {
        when(chartOfAccountsRepository.existsByCompanyId(COMPANY_ID)).thenReturn(false);
        List<SiewinPucRowDTO> rows = List.of(
                row(22, 0, 0, "Proveedores", 0),
                row(22, 5, 0, "Nacionales", 0),
                row(22, 5, 5, "Proveedores nacionales", 0),
                row4(22, 5, 5, 99, "Otro proveedor", 1),
                row4(22, 5, 5, 9999, "Proveedores varios", 1));

        assertThatThrownBy(() -> service.importFromSiewin(new SiewinPucImportRequest(rows)))
                .isInstanceOfSatisfying(InvalidOperationException.class, ex -> {
                    Map<String, Object> details = (Map<String, Object>) ex.getDetails();
                    assertThat((List<String>) details.get("catchAllCollisions")).hasSize(1);
                    assertThat((List<String>) details.get("duplicateCodes")).isEmpty();
                });
        verify(chartOfAccountService, never()).create(any());
    }

    @Test
    @DisplayName("ADD_MISSING: 9999 whose 99 already holds a different account in the plan is reported")
    @SuppressWarnings("unchecked")
    void addMissing_catchAllTakenInPlan_throws() {
        when(chartOfAccountsRepository.existsByCompanyId(COMPANY_ID)).thenReturn(true);
        when(chartOfAccountsRepository.findByCompanyIdOrderByCodeAsc(COMPANY_ID)).thenReturn(new ArrayList<>(List.of(
                existing(2L, "2", "Pasivo", false, true),
                existing(22L, "22", "Proveedores", false, true),
                existing(2205L, "2205", "Nacionales", false, true),
                existing(220505L, "220505", "Proveedores nacionales", false, true),
                existing(22050599L, "22050599", "Otro proveedor creado a mano", true, true))));
        List<SiewinPucRowDTO> rows = List.of(
                row(22, 0, 0, "Proveedores", 0),
                row(22, 5, 0, "Nacionales", 0),
                row(22, 5, 5, "Proveedores nacionales", 0),
                row4(22, 5, 5, 9999, "Proveedores varios", 1));

        assertThatThrownBy(() -> service.importFromSiewin(
                new SiewinPucImportRequest(rows, SiewinPucImportRequest.MODE_ADD_MISSING)))
                .isInstanceOfSatisfying(InvalidOperationException.class, ex -> {
                    Map<String, Object> details = (Map<String, Object>) ex.getDetails();
                    assertThat((List<String>) details.get("catchAllCollisions")).hasSize(1);
                });
        verify(chartOfAccountService, never()).create(any());
    }

    @Test
    @DisplayName("ADD_MISSING: 9999 already imported earlier as 99 (same name) is simply 'already existing'")
    void addMissing_catchAllAlreadyImported_isExisting() {
        when(chartOfAccountsRepository.existsByCompanyId(COMPANY_ID)).thenReturn(true);
        when(chartOfAccountsRepository.findByCompanyIdOrderByCodeAsc(COMPANY_ID)).thenReturn(new ArrayList<>(List.of(
                existing(2L, "2", "Pasivo", false, true),
                existing(22L, "22", "Proveedores", false, true),
                existing(2205L, "2205", "Nacionales", false, true),
                existing(220505L, "220505", "Proveedores nacionales", false, true),
                existing(22050599L, "22050599", "PROVEEDORES VARIOS", true, true))));
        List<SiewinPucRowDTO> rows = List.of(
                row(22, 0, 0, "Proveedores", 0),
                row(22, 5, 0, "Nacionales", 0),
                row(22, 5, 5, "Proveedores nacionales", 0),
                row4(22, 5, 5, 9999, "Proveedores varios", 1));

        SiewinPucImportResultDTO result = service.importFromSiewin(
                new SiewinPucImportRequest(rows, SiewinPucImportRequest.MODE_ADD_MISSING));

        verify(chartOfAccountService, never()).create(any());
        assertThat(result.accountsAlreadyExisting()).isEqualTo(4);
    }

    @Test
    @DisplayName("Groups 17, 21, 34, 52 and 62 (updated Navarra PUC) have a category and import cleanly")
    void fullImport_newStandardGroups_haveCategory() {
        when(chartOfAccountsRepository.existsByCompanyId(COMPANY_ID)).thenReturn(false);
        List<SiewinPucRowDTO> rows = List.of(
                row(17, 0, 0, "DIFERIDOS", 0),
                row(17, 5, 0, "GASTOS PAGADOS POR ANTICIPADO", 0),
                row(17, 5, 20, "SEGUROS Y FIANZAS", 0),
                row4(17, 5, 20, 1, "POLIZA AREAS COMUNES", 1),
                row(21, 0, 0, "OBLIGACIONES FINANCIERAS", 0),
                row(21, 20, 0, "COMPANIAS DE FINANCIAMIENTO COMERCIAL", 0),
                row(21, 20, 10, "ACEPTACIONES FINANCIERAS", 0),
                row4(21, 20, 10, 1, "INVERSORA PICHINCHA", 1),
                row(34, 0, 0, "REVALORIZACION DEL PATRIMONIO", 0),
                row(34, 5, 0, "AJUSTES POR INFLACION", 0),
                row(34, 5, 15, "DE RESERVAS", 0),
                row4(34, 5, 15, 1, "DE RESERVAS", 1),
                row(52, 0, 0, "OPERACIONALES DE VENTAS", 0),
                row(62, 0, 0, "COMPRAS", 0),
                row(62, 25, 0, "DEVOLUCION EN COMPRAS", 0),
                row(62, 25, 5, "DEVOLUCION EN COMPRAS", 0),
                row4(62, 25, 5, 1, "DEVOLUCION EN COMPRAS", 1));

        SiewinPucImportResultDTO result = service.importFromSiewin(new SiewinPucImportRequest(rows));

        assertThat(result.createdAccounts()).extracting(SiewinPucImportResultDTO.CreatedAccount::code)
                .contains("17052001", "21201001", "34051501", "52", "62250501");
        assertThat(result.createdAccounts()).filteredOn(a -> a.code().equals("62250501"))
                .singleElement().satisfies(a -> assertThat(a.nature()).isEqualTo("C"));
    }
}
