package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.SiewinBalanceImportRequest;
import com.erp.erp_cloud.dto.SiewinBalanceImportResultDTO;
import com.erp.erp_cloud.dto.SiewinBalanceRowDTO;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.DocumentType;
import com.erp.erp_cloud.entity.JournalEntry;
import com.erp.erp_cloud.entity.JournalEntryItem;
import com.erp.erp_cloud.entity.ThirdParty;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.repository.ThirdPartyRepository;
import com.erp.erp_cloud.security.context.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit tests for SiewinBalanceImportService -- no Spring context, no
 * database. Covers the business rules approved in
 * claude/analisis-migracion-saldos-siewin-2026-11-12.md: row
 * classification against the company's own catalogs, third-party
 * breakdown reconciliation, cost-center rejection, hierarchy
 * cross-validation, net-result posting, duplicate-block removal, and the
 * replace-via-neutralization flow for a re-upload.
 *
 * Runs via `./gradlew test` (no live MySQL needed).
 */
class SiewinBalanceImportServiceTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long GAIN_ACCOUNT_ID = 3605L;
    private static final Long LOSS_ACCOUNT_ID = 3601L;

    @Mock private ChartOfAccountsRepository accountRepository;
    @Mock private ThirdPartyRepository thirdPartyRepository;
    @Mock private JournalEntryRepository journalEntryRepository;
    @Mock private DocumentTypeService documentTypeService;
    @Mock private AccountingPeriodService accountingPeriodService;
    @Mock private EntityManager entityManager;

    private SiewinBalanceImportService service;
    private Company testCompany;
    private ChartOfAccounts gainAccount;
    private ChartOfAccounts lossAccount;
    private DocumentType openingDocType;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        service = new SiewinBalanceImportService(
                accountRepository, thirdPartyRepository, journalEntryRepository,
                documentTypeService, accountingPeriodService, entityManager);

        gainAccount = account("3605", "Utilidad del Ejercicio", true, false, false);
        gainAccount.setId(GAIN_ACCOUNT_ID);
        lossAccount = account("3601", "Pérdida del Ejercicio", true, false, false);
        lossAccount.setId(LOSS_ACCOUNT_ID);

        testCompany = new Company();
        testCompany.setId(COMPANY_ID);
        testCompany.setDefaultGainAccount(gainAccount);
        testCompany.setDefaultLossAccount(lossAccount);
        TenantContext.setContext(testCompany);

        openingDocType = new DocumentType();
        openingDocType.setId(77L);
        openingDocType.setCode("APERTURA");
        openingDocType.setPrefix("APERTURA");
        when(documentTypeService.findOrCreateOpeningDocumentType(COMPANY_ID)).thenReturn(openingDocType);
        when(documentTypeService.getNextConsecutive(77L)).thenReturn(1L);

        when(entityManager.getReference(eq(Company.class), any(Long.class))).thenReturn(testCompany);
        when(entityManager.getReference(eq(ChartOfAccounts.class), any())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(1);
            if (id.equals(GAIN_ACCOUNT_ID)) return gainAccount;
            if (id.equals(LOSS_ACCOUNT_ID)) return lossAccount;
            ChartOfAccounts stub = new ChartOfAccounts();
            stub.setId(id);
            return stub;
        });
        when(entityManager.getReference(eq(ThirdParty.class), any())).thenAnswer(invocation -> {
            ThirdParty tp = new ThirdParty();
            tp.setId(invocation.getArgument(1));
            return tp;
        });

        when(journalEntryRepository.findLiveByCompanyIdAndDocumentTypeCode(COMPANY_ID, "APERTURA"))
                .thenReturn(List.of());
        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(invocation -> {
            JournalEntry e = invocation.getArgument(0);
            if (e.getId() == null) e.setId(500L);
            return e;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private ChartOfAccounts account(String code, String name, boolean posting,
                                     boolean requiresThirdParty, boolean requiresCostCenter) {
        ChartOfAccounts a = new ChartOfAccounts();
        a.setCode(code);
        a.setName(name);
        a.setPostingAccount(posting);
        a.setRequiresThirdParty(requiresThirdParty);
        a.setRequiresCostCenter(requiresCostCenter);
        return a;
    }

    private void registerAccounts(ChartOfAccounts... accounts) {
        List<ChartOfAccounts> all = new ArrayList<>(List.of(accounts));
        all.add(gainAccount);
        all.add(lossAccount);
        long nextId = 1000L;
        for (ChartOfAccounts a : all) {
            if (a.getId() == null) {
                a.setId(nextId++);
            }
        }
        when(accountRepository.findByCompanyIdAndActiveTrueOrderByCodeAsc(COMPANY_ID)).thenReturn(all);
    }

    private SiewinBalanceRowDTO row(int idx, String code, String name, String amount) {
        return new SiewinBalanceRowDTO(idx, code, name, amount == null ? null : new BigDecimal(amount));
    }

    private SiewinBalanceImportRequest request(List<SiewinBalanceRowDTO> rows) {
        return new SiewinBalanceImportRequest(2026, rows);
    }

    // ---------------------------------------------------------------
    // Happy path
    // ---------------------------------------------------------------

    @Test
    @DisplayName("importFromSiewin() posts a balanced opening entry and computes the net result as a gain")
    void importFromSiewin_happyPath_postsEntryWithGain() {
        ChartOfAccounts cash = account("11", "Caja", true, false, false);
        ChartOfAccounts capital = account("31", "Capital Social", true, false, false);
        registerAccounts(cash, capital);

        // Activo 1,000 (débito) = Pasivo/Patrimonio -700 (crédito) + resultado 300 (utilidad)
        List<SiewinBalanceRowDTO> rows = List.of(
                row(1, "11", "Caja", "1000.00"),
                row(2, "31", "Capital Social", "-700.00")
        );

        SiewinBalanceImportResultDTO result = service.importFromSiewin(request(rows), "jaime");

        assertThat(result.accountLinesPosted()).isEqualTo(2);
        assertThat(result.thirdPartyLinesPosted()).isEqualTo(0);
        assertThat(result.netResult()).isEqualTo(new BigDecimal("300.00"));
        assertThat(result.replacedExistingEntry()).isFalse();

        org.mockito.ArgumentCaptor<JournalEntry> captor = org.mockito.ArgumentCaptor.forClass(JournalEntry.class);
        verify(journalEntryRepository).save(captor.capture());
        JournalEntry saved = captor.getValue();
        assertThat(saved.getItems()).hasSize(3); // caja + capital + utilidad

        BigDecimal totalDebit = saved.getItems().stream().map(JournalEntryItem::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = saved.getItems().stream().map(JournalEntryItem::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalDebit).isEqualByComparingTo(totalCredit);

        boolean hasResultLine = saved.getItems().stream().anyMatch(i -> i.getAccount() == gainAccount);
        assertThat(hasResultLine).isTrue();
    }

    @Test
    @DisplayName("importFromSiewin() reconciles a third-party breakdown that sums to the account's printed balance")
    void importFromSiewin_thirdPartyBreakdown_matches() {
        ChartOfAccounts clients = account("1305", "Clientes Nacionales", true, true, false);
        registerAccounts(clients);

        ThirdParty tp1 = new ThirdParty();
        tp1.setId(1L);
        tp1.setDocumentNumber("800197268");
        ThirdParty tp2 = new ThirdParty();
        tp2.setId(2L);
        tp2.setDocumentNumber("900123456");
        when(thirdPartyRepository.findByCompanyIdAndDocumentNumber(COMPANY_ID, "800197268")).thenReturn(Optional.of(tp1));
        when(thirdPartyRepository.findByCompanyIdAndDocumentNumber(COMPANY_ID, "900123456")).thenReturn(Optional.of(tp2));

        List<SiewinBalanceRowDTO> rows = List.of(
                row(1, "13-05", "Clientes Nacionales", "1500.00"),
                row(2, "800197268", "Cliente Uno", "1000.00"),
                row(3, "900123456", "Cliente Dos", "500.00")
        );

        SiewinBalanceImportResultDTO result = service.importFromSiewin(request(rows), "jaime");

        assertThat(result.accountLinesPosted()).isEqualTo(0);
        assertThat(result.thirdPartyLinesPosted()).isEqualTo(2);
        // El activo queda sin contrapartida -> se postea como pérdida (clientes es la única línea).
        assertThat(result.netResult()).isEqualTo(new BigDecimal("1500.00"));
    }

    // ---------------------------------------------------------------
    // Validation failures (Fail-Fast, all collected into one exception)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("importFromSiewin() rejects a row whose account code isn't in the company's PUC")
    void importFromSiewin_accountNotFound_throws() {
        registerAccounts(); // empresa sin PUC migrado

        List<SiewinBalanceRowDTO> rows = List.of(row(1, "11", "Caja", "1000.00"));

        assertThatThrownBy(() -> service.importFromSiewin(request(rows), "jaime"))
                .isInstanceOfSatisfying(InvalidOperationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("SIEWIN_BALANCE_IMPORT_INTEGRITY_GAP"));

        verify(journalEntryRepository, never()).save(any(JournalEntry.class));
    }

    @Test
    @DisplayName("importFromSiewin() matches a dashed file code against the digits-only PUC code")
    void importFromSiewin_dashedCode_matchesDigitsOnlyPuc() {
        // El PUC migrado guarda "1105"; el Inventario y Balance imprime "11-05".
        ChartOfAccounts cash = account("1105", "Caja", true, false, false);
        registerAccounts(cash);

        List<SiewinBalanceRowDTO> rows = List.of(row(1, "11-05", "Caja", "1000.00"));

        SiewinBalanceImportResultDTO result = service.importFromSiewin(request(rows), "jaime");

        assertThat(result).isNotNull();
        verify(journalEntryRepository).save(any(JournalEntry.class));
    }

    @Test
    @DisplayName("currentOpeningStatus() reports the live APERTURA entry, or none")
    void currentOpeningStatus() {
        assertThat(service.currentOpeningStatus().exists()).isFalse();

        JournalEntry live = new JournalEntry();
        live.setDocumentNumber("APERTURA-1");
        live.setEntryDate(java.time.LocalDate.of(2025, 12, 31));
        live.getItems().add(new JournalEntryItem());
        live.getItems().add(new JournalEntryItem());
        when(journalEntryRepository.findLiveByCompanyIdAndDocumentTypeCode(COMPANY_ID, "APERTURA"))
                .thenReturn(List.of(live));

        com.erp.erp_cloud.dto.OpeningBalanceStatusDTO status = service.currentOpeningStatus();
        assertThat(status.exists()).isTrue();
        assertThat(status.documentNumber()).isEqualTo("APERTURA-1");
        assertThat(status.entryDate()).isEqualTo(java.time.LocalDate.of(2025, 12, 31));
        assertThat(status.lineCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("importFromSiewin() sums the same third party printed in several rows of ONE account block")
    void importFromSiewin_sameThirdPartySplitInRows_isSummed() {
        // Caso real Navarra 23-65-70-01: el mismo tercero en varios renglones,
        // y la suma de todos los renglones es el saldo de la cuenta.
        ChartOfAccounts withholding = account("23657001", "Otras retenciones", true, true, false);
        ChartOfAccounts cash = account("11050501", "Caja", true, false, false);
        registerAccounts(withholding, cash);
        ThirdParty navarra = new ThirdParty();
        navarra.setId(801L);
        navarra.setDocumentNumber("801004463");
        ThirdParty octavio = new ThirdParty();
        octavio.setId(193L);
        octavio.setDocumentNumber("19310194");
        when(thirdPartyRepository.findByCompanyIdAndDocumentNumber(COMPANY_ID, "801004463")).thenReturn(Optional.of(navarra));
        when(thirdPartyRepository.findByCompanyIdAndDocumentNumber(COMPANY_ID, "19310194")).thenReturn(Optional.of(octavio));

        List<SiewinBalanceRowDTO> rows = List.of(
                row(1, "11-05-05-01", "Caja", "100.00"),
                row(2, "23-65-70-01", "Otras retenciones", "-100.00"),
                row(3, "801004463", "Navarra", "-70.00"),
                row(4, "19310194", "Octavio", "-50.00"),
                row(5, "801004463", "Navarra", "-30.00"),
                row(6, "19310194", "Octavio", "50.00"));

        SiewinBalanceImportResultDTO result = service.importFromSiewin(request(rows), "jaime");

        // caja + navarra (-100); octavio se anula (0) y no genera línea
        assertThat(result.accountLinesPosted()).isEqualTo(1);
        assertThat(result.thirdPartyLinesPosted()).isEqualTo(1);
        org.mockito.ArgumentCaptor<JournalEntry> captor = org.mockito.ArgumentCaptor.forClass(JournalEntry.class);
        verify(journalEntryRepository).save(captor.capture());
        JournalEntryItem navarraLine = captor.getValue().getItems().stream()
                .filter(i -> i.getThirdParty() != null).findFirst().orElseThrow();
        assertThat(navarraLine.getCredit()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("importFromSiewin() maps 4-digit SIEWIN levels (0001, 9999) like the PUC importer")
    void importFromSiewin_fourDigitLevels_matchPucCodes() {
        ChartOfAccounts caja = account("11050501", "Caja general", true, false, false);
        ChartOfAccounts varios = account("22050599", "Proveedores varios", true, false, false);
        registerAccounts(caja, varios);

        List<SiewinBalanceRowDTO> rows = List.of(
                row(1, "11-05-05-0001", "Caja general", "1000.00"),
                row(2, "22-05-05-9999", "Proveedores varios", "-400.00"));

        SiewinBalanceImportResultDTO result = service.importFromSiewin(request(rows), "jaime");

        assertThat(result.accountLinesPosted()).isEqualTo(2);
    }

    @Test
    @DisplayName("importFromSiewin() reports an existing-but-inactive account apart from a missing one")
    @SuppressWarnings("unchecked")
    void importFromSiewin_inactiveAccount_reportedSeparately() {
        registerAccounts(); // la 1105 no está entre las activas
        ChartOfAccounts inactiveCash = account("1105", "Caja", true, false, false);
        inactiveCash.setActive(false);
        when(accountRepository.findByCompanyIdAndCode(COMPANY_ID, "1105")).thenReturn(Optional.of(inactiveCash));

        List<SiewinBalanceRowDTO> rows = List.of(
                row(1, "11-05", "Caja", "1000.00"),
                row(2, "11-10", "Bancos", "500.00"));

        assertThatThrownBy(() -> service.importFromSiewin(request(rows), "jaime"))
                .isInstanceOfSatisfying(InvalidOperationException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo("SIEWIN_BALANCE_IMPORT_INTEGRITY_GAP");
                    java.util.Map<String, Object> details = (java.util.Map<String, Object>) ex.getDetails();
                    assertThat((List<String>) details.get("accountInactive")).containsExactly("fila 1: 11-05 - Caja");
                    assertThat((List<String>) details.get("accountNotFound")).containsExactly("fila 2: 11-10 - Bancos");
                });

        verify(journalEntryRepository, never()).save(any(JournalEntry.class));
    }

    @Test
    @DisplayName("importFromSiewin() rejects a third-party breakdown that doesn't sum to the account's printed balance")
    void importFromSiewin_thirdPartyMismatch_throws() {
        ChartOfAccounts clients = account("1305", "Clientes Nacionales", true, true, false);
        registerAccounts(clients);

        ThirdParty tp1 = new ThirdParty();
        tp1.setId(1L);
        tp1.setDocumentNumber("800197268");
        when(thirdPartyRepository.findByCompanyIdAndDocumentNumber(COMPANY_ID, "800197268")).thenReturn(Optional.of(tp1));

        List<SiewinBalanceRowDTO> rows = List.of(
                row(1, "13-05", "Clientes Nacionales", "1500.00"),
                row(2, "800197268", "Cliente Uno", "1000.00") // falta 500 para cuadrar
        );

        assertThatThrownBy(() -> service.importFromSiewin(request(rows), "jaime"))
                .isInstanceOfSatisfying(InvalidOperationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("SIEWIN_BALANCE_IMPORT_INTEGRITY_GAP"));
    }

    @Test
    @DisplayName("importFromSiewin() rejects the whole file when a cost-center-required account has a non-zero balance")
    void importFromSiewin_costCenterRequired_throws() {
        ChartOfAccounts ccAccount = account("1310", "Cuentas por Cobrar con CC", true, false, true);
        registerAccounts(ccAccount);

        List<SiewinBalanceRowDTO> rows = List.of(row(1, "13-10", "Cuentas por Cobrar con CC", "500.00"));

        assertThatThrownBy(() -> service.importFromSiewin(request(rows), "jaime"))
                .isInstanceOfSatisfying(InvalidOperationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("SIEWIN_BALANCE_IMPORT_INTEGRITY_GAP"));
    }

    @Test
    @DisplayName("importFromSiewin() rejects when a printed group subtotal doesn't match the recalculated leaves")
    void importFromSiewin_outOfBalance_throws() {
        ChartOfAccounts group = account("13", "Deudores", false, false, false);
        ChartOfAccounts leaf = account("1305", "Clientes Nacionales", true, false, false);
        registerAccounts(group, leaf);

        List<SiewinBalanceRowDTO> rows = List.of(
                row(1, "13", "Deudores", "9999.00"), // no coincide con la hoja
                row(2, "13-05", "Clientes Nacionales", "1500.00")
        );

        assertThatThrownBy(() -> service.importFromSiewin(request(rows), "jaime"))
                .isInstanceOfSatisfying(InvalidOperationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("SIEWIN_BALANCE_OUT_OF_BALANCE"));
    }

    @Test
    @DisplayName("importFromSiewin() requires the default gain/loss accounts to be configured when there's a net result")
    void importFromSiewin_closingAccountsNotConfigured_throws() {
        testCompany.setDefaultGainAccount(null);
        testCompany.setDefaultLossAccount(null);

        ChartOfAccounts cash = account("11", "Caja", true, false, false);
        registerAccounts(cash);

        List<SiewinBalanceRowDTO> rows = List.of(row(1, "11", "Caja", "1000.00"));

        assertThatThrownBy(() -> service.importFromSiewin(request(rows), "jaime"))
                .isInstanceOfSatisfying(InvalidOperationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("SIEWIN_BALANCE_CLOSING_ACCOUNTS_NOT_CONFIGURED"));
    }

    @Test
    @DisplayName("importFromSiewin() rejects an empty file")
    void importFromSiewin_emptyFile_throws() {
        assertThatThrownBy(() -> service.importFromSiewin(request(List.of()), "jaime"))
                .isInstanceOfSatisfying(InvalidOperationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("SIEWIN_BALANCE_EMPTY_FILE"));
    }

    @Test
    @DisplayName("importFromSiewin() rejects a missing closing year")
    void importFromSiewin_missingYear_throws() {
        SiewinBalanceImportRequest req = new SiewinBalanceImportRequest(null, List.of(row(1, "11", "Caja", "1000.00")));

        assertThatThrownBy(() -> service.importFromSiewin(req, "jaime"))
                .isInstanceOfSatisfying(InvalidOperationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("SIEWIN_BALANCE_MISSING_YEAR"));
    }

    // ---------------------------------------------------------------
    // Duplicate-block removal (SIEWIN RPV v.6 known bug)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("importFromSiewin() silently drops an exact-duplicate consecutive block of rows")
    void importFromSiewin_duplicateBlock_removed() {
        ChartOfAccounts cash = account("11", "Caja", true, false, false);
        ChartOfAccounts capital = account("31", "Capital Social", true, false, false);
        registerAccounts(cash, capital);

        // El bloque de 2 filas [Caja, Capital] aparece duplicado dos veces
        // seguidas -- bug conocido de SIEWIN RPV v.6 (sección 6.2). La
        // deduplicación solo opera sobre bloques de 2+ filas (los bloques
        // reales confirmados contra el archivo de Navarra nunca fueron de
        // una sola fila), así que este caso es el que ejercita esa
        // deduplicación explícita.
        List<SiewinBalanceRowDTO> rows = List.of(
                row(1, "11", "Caja", "1000.00"),
                row(2, "31", "Capital Social", "-1000.00"),
                row(3, "11", "Caja", "1000.00"),
                row(4, "31", "Capital Social", "-1000.00")
        );

        SiewinBalanceImportResultDTO result = service.importFromSiewin(request(rows), "jaime");

        assertThat(result.accountLinesPosted()).isEqualTo(2); // caja + capital, no duplicado
        assertThat(result.netResult()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.duplicateBlocksRemoved()).isNotEmpty();
    }

    // ---------------------------------------------------------------
    // Replace-via-neutralization (re-upload during Jan-Apr)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("importFromSiewin() annuls the previous live APERTURA entry and posts a new one on re-upload")
    void importFromSiewin_replacesExistingOpeningEntry() {
        ChartOfAccounts cash = account("11", "Caja", true, false, false);
        registerAccounts(cash);

        JournalEntry previous = new JournalEntry();
        previous.setId(111L);
        previous.setAnnulled(false);
        JournalEntryItem oldItem = new JournalEntryItem();
        oldItem.setAccount(cash);
        oldItem.setDebit(new BigDecimal("500.00"));
        oldItem.setCredit(BigDecimal.ZERO);
        previous.addItem(oldItem);
        when(journalEntryRepository.findLiveByCompanyIdAndDocumentTypeCode(COMPANY_ID, "APERTURA"))
                .thenReturn(List.of(previous));

        List<SiewinBalanceRowDTO> rows = List.of(row(1, "11", "Caja", "1000.00"));

        SiewinBalanceImportResultDTO result = service.importFromSiewin(request(rows), "jaime");

        assertThat(result.replacedExistingEntry()).isTrue();
        assertThat(previous.isAnnulled()).isTrue();
        assertThat(previous.getItems().get(0).getDebit()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(journalEntryRepository, org.mockito.Mockito.times(2)).save(any(JournalEntry.class)); // anula + postea
    }

    @Test
    @DisplayName("importFromSiewin() rejects when more than one live APERTURA entry already exists")
    void importFromSiewin_multipleOpeningEntries_throws() {
        when(journalEntryRepository.findLiveByCompanyIdAndDocumentTypeCode(COMPANY_ID, "APERTURA"))
                .thenReturn(List.of(new JournalEntry(), new JournalEntry()));

        List<SiewinBalanceRowDTO> rows = List.of(row(1, "11", "Caja", "1000.00"));

        assertThatThrownBy(() -> service.importFromSiewin(request(rows), "jaime"))
                .isInstanceOfSatisfying(InvalidOperationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("SIEWIN_BALANCE_MULTIPLE_OPENING_ENTRIES_FOUND"));

        verify(journalEntryRepository, never()).save(any(JournalEntry.class));
    }

    // ---------------------------------------------------------------
    // Safety pre-check (section 2.5): reuses AccountingPeriodService's
    // own FISCAL_YEAR_CLOSED / ACCOUNTING_PERIOD_CLOSED guard.
    // ---------------------------------------------------------------

    @Test
    @DisplayName("importFromSiewin() propagates AccountingPeriodService's fiscal-year-closed guard untouched")
    void importFromSiewin_fiscalYearClosed_propagates() {
        org.mockito.Mockito.doThrow(new InvalidOperationException("The Fiscal Year 2026 is CLOSED.", "FISCAL_YEAR_CLOSED"))
                .when(accountingPeriodService).validateDateIsOpen(any(), eq(COMPANY_ID));

        List<SiewinBalanceRowDTO> rows = List.of(row(1, "11", "Caja", "1000.00"));

        assertThatThrownBy(() -> service.importFromSiewin(request(rows), "jaime"))
                .isInstanceOfSatisfying(InvalidOperationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FISCAL_YEAR_CLOSED"));

        verify(journalEntryRepository, never()).save(any(JournalEntry.class));
    }
}
