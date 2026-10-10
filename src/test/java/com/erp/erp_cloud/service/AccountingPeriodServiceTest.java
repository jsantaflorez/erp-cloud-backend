package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.AccountingPeriodResponseDTO;
import com.erp.erp_cloud.entity.AccountingPeriod;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.DocumentType;
import com.erp.erp_cloud.entity.JournalEntry;
import com.erp.erp_cloud.enums.AccountClass;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.exception.ResourceNotFoundException;
import com.erp.erp_cloud.repository.AccountOpeningBalanceRepository;
import com.erp.erp_cloud.repository.AccountingPeriodRepository;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.repository.CompanyRepository;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.security.context.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit tests for AccountingPeriodService -- no Spring context, no
 * database. This service has no manual QA checklist section (it postdates
 * checklist-pruebas-erp.md), so coverage here is derived straight from the
 * business logic in the service: period open/close, the year-end blanket
 * lock, reopening, and (2026-10-03) the year-end closing entry + opening
 * balance snapshot generation.
 *
 * Runs via `./gradlew test` (no live MySQL needed).
 */
class AccountingPeriodServiceTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long GAIN_ACCOUNT_ID = 3605L;
    private static final Long LOSS_ACCOUNT_ID = 3601L;

    @Mock private AccountingPeriodRepository repository;
    @Mock private ChartOfAccountsRepository accountRepository;
    @Mock private JournalEntryRepository journalEntryRepository;
    @Mock private AccountOpeningBalanceRepository openingBalanceRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private DocumentTypeService documentTypeService;
    @Mock private EntityManager entityManager;

    private AccountingPeriodService service;
    private Company testCompany;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentTenant(COMPANY_ID);

        service = new AccountingPeriodService(
                repository, accountRepository, journalEntryRepository, openingBalanceRepository,
                companyRepository, documentTypeService, entityManager);

        testCompany = new Company();
        testCompany.setId(COMPANY_ID);

        when(entityManager.getReference(eq(Company.class), any(Long.class))).thenReturn(testCompany);
        when(repository.save(any(AccountingPeriod.class))).thenAnswer(invocation -> {
            AccountingPeriod p = invocation.getArgument(0);
            if (p.getId() == null) p.setId(100L);
            return p;
        });
        // closeYear()/reopenYear() default to "no next/prior year on record"
        // unless a test explicitly stubs otherwise -- Mockito's default
        // answer for an unstubbed List-returning call is an empty list.
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private AccountingPeriod existingPeriod(Integer year, Integer month, boolean open) {
        AccountingPeriod p = new AccountingPeriod();
        p.setId(5L);
        p.setYear(year);
        p.setMonth(month);
        p.setOpen(open);
        p.setCompany(testCompany);
        return p;
    }

    private ChartOfAccounts equityAccount(Long id, String code, boolean requiresThirdParty, boolean requiresCostCenter) {
        ChartOfAccounts a = new ChartOfAccounts();
        a.setId(id);
        a.setCode(code);
        a.setName("Cuenta " + code);
        a.setCompany(testCompany);
        a.setAccountClass(AccountClass.EQUITY);
        a.setPostingAccount(true);
        a.setRequiresThirdParty(requiresThirdParty);
        a.setRequiresCostCenter(requiresCostCenter);
        a.setClosesAtYearEnd(false);
        return a;
    }

    private ChartOfAccounts resultAccount(Long id, String code, boolean requiresThirdParty, boolean requiresCostCenter) {
        ChartOfAccounts a = new ChartOfAccounts();
        a.setId(id);
        a.setCode(code);
        a.setName("Cuenta " + code);
        a.setCompany(testCompany);
        a.setAccountClass(AccountClass.REVENUE);
        a.setPostingAccount(true);
        a.setRequiresThirdParty(requiresThirdParty);
        a.setRequiresCostCenter(requiresCostCenter);
        a.setClosesAtYearEnd(true);
        return a;
    }

    /**
     * Wires the minimum stubs every closeYear() test needs: valid
     * Ganancia/Pérdida equity accounts, a closing DocumentType, a
     * consecutive number, and a no-op save/saveAll for the closing entry
     * and the opening-balance snapshot. Individual tests still stub
     * getYearEndBalancesByAccount() themselves, since that's what varies
     * per scenario.
     */
    private void stubClosingInfrastructure() {
        when(accountRepository.findById(GAIN_ACCOUNT_ID))
                .thenReturn(Optional.of(equityAccount(GAIN_ACCOUNT_ID, "360501", false, false)));
        when(accountRepository.findById(LOSS_ACCOUNT_ID))
                .thenReturn(Optional.of(equityAccount(LOSS_ACCOUNT_ID, "360101", false, false)));

        DocumentType closingDocType = new DocumentType();
        closingDocType.setId(99L);
        closingDocType.setCode("CIERRE");
        closingDocType.setPrefix("CIERRE");
        when(documentTypeService.findOrCreateClosingDocumentType(COMPANY_ID)).thenReturn(closingDocType);
        when(documentTypeService.getNextConsecutive(99L)).thenReturn(1L);

        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(invocation -> {
            JournalEntry e = invocation.getArgument(0);
            if (e.getId() == null) e.setId(500L);
            return e;
        });
        when(accountRepository.findByCompanyIdAndActiveTrueOrderByCodeAsc(COMPANY_ID)).thenReturn(List.of());
    }

    // ============================================================
    // findAllByCompany() -- virtual "Abierto" rows for months with
    // activity but no period record yet (2026-10-10)
    // ============================================================

    @Test
    @DisplayName("findAllByCompany() scaffolds a virtual Abierto row for a month with activity but no period record")
    void findAllByCompany_scaffoldsOpenRowForMonthWithActivityButNoRecord() {
        AccountingPeriod marchClosed = existingPeriod(2026, 3, false);
        when(repository.findByCompanyIdOrderByYearDescMonthDesc(COMPANY_ID)).thenReturn(List.of(marchClosed));
        when(journalEntryRepository.findDistinctEntryDates(eq(COMPANY_ID), isNull(), isNull()))
                .thenReturn(List.of(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 6, 5)));

        List<AccountingPeriodResponseDTO> result = service.findAllByCompany();

        assertThat(result).hasSize(2);

        AccountingPeriodResponseDTO june = result.stream()
                .filter(p -> p.getMonth() == 6).findFirst().orElseThrow();
        assertThat(june.getYear()).isEqualTo(2026);
        assertThat(june.isOpen()).isTrue();
        assertThat(june.getId()).isNull(); // scaffolded, never persisted

        AccountingPeriodResponseDTO march = result.stream()
                .filter(p -> p.getMonth() == 3).findFirst().orElseThrow();
        assertThat(march.getId()).isNotNull(); // the real record -- not duplicated by the scaffold
    }

    @Test
    @DisplayName("findAllByCompany() does not scaffold a row for a month that already has a period record")
    void findAllByCompany_doesNotDuplicateExistingPeriod() {
        AccountingPeriod marchClosed = existingPeriod(2026, 3, false);
        when(repository.findByCompanyIdOrderByYearDescMonthDesc(COMPANY_ID)).thenReturn(List.of(marchClosed));
        when(journalEntryRepository.findDistinctEntryDates(eq(COMPANY_ID), isNull(), isNull()))
                .thenReturn(List.of(LocalDate.of(2026, 3, 10)));

        List<AccountingPeriodResponseDTO> result = service.findAllByCompany();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isNotNull();
    }

    // ============================================================
    // closePeriod()
    // ============================================================

    @Test
    @DisplayName("closePeriod() creates a new period record when none exists yet, and closes it")
    void closePeriod_createsNewPeriod_whenNoneExists() {
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 3)).thenReturn(Optional.empty());

        AccountingPeriodResponseDTO result = service.closePeriod(2026, 3, "jaime", "month-end close");

        ArgumentCaptor<AccountingPeriod> captor = ArgumentCaptor.forClass(AccountingPeriod.class);
        verify(repository).save(captor.capture());
        AccountingPeriod saved = captor.getValue();

        assertThat(saved.getCompany()).isEqualTo(testCompany);
        assertThat(saved.getYear()).isEqualTo(2026);
        assertThat(saved.getMonth()).isEqualTo(3);
        assertThat(saved.isOpen()).isFalse();
        assertThat(saved.isYearClose()).isFalse();
        assertThat(saved.getClosedBy()).isEqualTo("jaime");
        assertThat(saved.getClosingNotes()).isEqualTo("month-end close");
        assertThat(saved.getClosedAt()).isNotNull();
        assertThat(result.isOpen()).isFalse();

        // FIX regression guard: mapToResponseDTO() used to silently drop
        // periodCode/closingNotes/reopenedAt/reopenedBy/reopeningNotes even
        // though the DTO declares all of them -- the Periodos Contables
        // screen needs the full audit trail, not just closedBy/closedAt.
        assertThat(result.getPeriodCode()).isEqualTo("2026-03");
        assertThat(result.getClosingNotes()).isEqualTo("month-end close");
        assertThat(result.getClosedBy()).isEqualTo("jaime");
        assertThat(result.getReopenedAt()).isNull();
        assertThat(result.getReopenedBy()).isNull();
        assertThat(result.getReopeningNotes()).isNull();
    }

    @Test
    @DisplayName("closePeriod() closes an already-existing period record instead of duplicating it")
    void closePeriod_updatesExistingPeriod_whenAlreadyExists() {
        AccountingPeriod existing = existingPeriod(2026, 3, true);
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 3)).thenReturn(Optional.of(existing));

        service.closePeriod(2026, 3, "jaime", "close");

        ArgumentCaptor<AccountingPeriod> captor = ArgumentCaptor.forClass(AccountingPeriod.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(existing.isOpen()).isFalse();
    }

    @Test
    @DisplayName("closePeriod() rejects closing a month that is already closed -- must be reopened first")
    void closePeriod_alreadyClosed_throws() {
        AccountingPeriod alreadyClosed = existingPeriod(2026, 3, false); // open = false
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 3)).thenReturn(Optional.of(alreadyClosed));

        assertThatThrownBy(() -> service.closePeriod(2026, 3, "jaime", "close again"))
                .isInstanceOf(InvalidOperationException.class)
                .satisfies(ex -> assertThat(((InvalidOperationException) ex).getErrorCode())
                        .isEqualTo("PERIOD_ALREADY_CLOSED"));

        verify(repository, never()).save(any(AccountingPeriod.class));
    }

    @Test
    @DisplayName("closePeriod() rejects a year outside 1900-2100")
    void closePeriod_invalidYear_throws() {
        assertThatThrownBy(() -> service.closePeriod(1899, 1, "jaime", "x"))
                .isInstanceOf(InvalidOperationException.class);
        assertThatThrownBy(() -> service.closePeriod(2101, 1, "jaime", "x"))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    @DisplayName("closePeriod() rejects a month outside 1-12")
    void closePeriod_invalidMonth_throws() {
        assertThatThrownBy(() -> service.closePeriod(2026, 0, "jaime", "x"))
                .isInstanceOf(InvalidOperationException.class);
        assertThatThrownBy(() -> service.closePeriod(2026, 13, "jaime", "x"))
                .isInstanceOf(InvalidOperationException.class);
    }

    private List<AccountingPeriod> allMonthsClosedExceptDecember(Integer year) {
        List<AccountingPeriod> periods = new java.util.ArrayList<>();
        for (int month = 1; month <= 11; month++) {
            periods.add(existingPeriod(year, month, false)); // closed
        }
        return periods;
    }

    // ============================================================
    // closeYear() -- months/prior-year validation (2026-10-03 rewrite)
    // ============================================================

    @Test
    @DisplayName("closeYear() rejects sealing the year while some months are still open or missing a record")
    void closeYear_monthsNotAllClosed_throws() {
        List<AccountingPeriod> periods = allMonthsClosedExceptDecember(2026);
        periods.get(4).setOpen(true); // month 5 (index 4) is still open
        // month 8 has no record at all -- simulate by removing it
        periods.removeIf(p -> p.getMonth() == 8);
        when(repository.findByCompanyIdAndYear(COMPANY_ID, 2026)).thenReturn(periods);
        // Both months must show real activity -- since 2026-10-10, an
        // empty month no longer blocks the year-end close on its own.
        when(journalEntryRepository.findDistinctEntryDates(eq(COMPANY_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(LocalDate.of(2026, 5, 10), LocalDate.of(2026, 8, 20)));

        assertThatThrownBy(() -> service.closeYear(2026, "jaime", "annual close", GAIN_ACCOUNT_ID, LOSS_ACCOUNT_ID))
                .isInstanceOf(InvalidOperationException.class)
                .satisfies(ex -> {
                    InvalidOperationException ioe = (InvalidOperationException) ex;
                    assertThat(ioe.getErrorCode()).isEqualTo("MONTHS_NOT_CLOSED_BEFORE_YEAR_END");
                    assertThat(ioe.getMessage()).contains("5").contains("8");
                });

        // Must never reach the actual close/save step, nor touch the
        // closing-entry machinery.
        verify(repository, never()).save(any(AccountingPeriod.class));
        verify(journalEntryRepository, never()).save(any(JournalEntry.class));
    }

    @Test
    @DisplayName("closeYear() rejects sealing the year when a month has real activity but no period record at all")
    void closeYear_noPeriodsExistYet_throws() {
        when(repository.findByCompanyIdAndYear(COMPANY_ID, 2026)).thenReturn(List.of());
        when(journalEntryRepository.findDistinctEntryDates(eq(COMPANY_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(LocalDate.of(2026, 3, 15)));

        assertThatThrownBy(() -> service.closeYear(2026, "jaime", "annual close", GAIN_ACCOUNT_ID, LOSS_ACCOUNT_ID))
                .isInstanceOf(InvalidOperationException.class)
                .satisfies(ex -> assertThat(((InvalidOperationException) ex).getErrorCode())
                        .isEqualTo("MONTHS_NOT_CLOSED_BEFORE_YEAR_END"));
    }

    @Test
    @DisplayName("closeYear() does NOT require closing a month that has zero journal-entry activity (company's books start mid-year)")
    void closeYear_monthsWithNoActivity_doNotBlockClosing() {
        // Company's fiscal year only has entries in March and December;
        // March is closed, the other ten months (including the eight
        // that never had any activity at all, e.g. a books-start in
        // November scenario) have no period record whatsoever.
        AccountingPeriod march = existingPeriod(2026, 3, false); // closed
        when(repository.findByCompanyIdAndYear(COMPANY_ID, 2026)).thenReturn(List.of(march));
        when(journalEntryRepository.findDistinctEntryDates(eq(COMPANY_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(LocalDate.of(2026, 3, 10)));
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 12)).thenReturn(Optional.empty());
        when(journalEntryRepository.getYearEndBalancesByAccount(eq(COMPANY_ID), any(LocalDate.class), eq(true)))
                .thenReturn(List.of());
        when(journalEntryRepository.getYearEndBalancesByAccount(eq(COMPANY_ID), any(LocalDate.class), eq(false)))
                .thenReturn(List.of());
        stubClosingInfrastructure();

        AccountingPeriodResponseDTO result =
                service.closeYear(2026, "jaime", "annual close", GAIN_ACCOUNT_ID, LOSS_ACCOUNT_ID);

        assertThat(result.isYearClose()).isTrue();
    }

    @Test
    @DisplayName("closeYear() rejects closing the year when the prior year exists but is not closed")
    void closeYear_priorYearNotClosed_throws() {
        when(repository.findByCompanyIdAndYear(COMPANY_ID, 2026)).thenReturn(allMonthsClosedExceptDecember(2026));
        AccountingPeriod priorYearDecember = existingPeriod(2025, 12, false);
        priorYearDecember.setYearClose(false); // closed individually, but year never sealed
        when(repository.findByCompanyIdAndYear(COMPANY_ID, 2025)).thenReturn(List.of(priorYearDecember));

        assertThatThrownBy(() -> service.closeYear(2026, "jaime", "annual close", GAIN_ACCOUNT_ID, LOSS_ACCOUNT_ID))
                .isInstanceOf(InvalidOperationException.class)
                .satisfies(ex -> assertThat(((InvalidOperationException) ex).getErrorCode())
                        .isEqualTo("PRIOR_YEAR_NOT_CLOSED"));

        verify(journalEntryRepository, never()).save(any(JournalEntry.class));
    }

    @Test
    @DisplayName("closeYear() proceeds when the prior year does not exist at all (company's first fiscal year)")
    void closeYear_noPriorYearOnRecord_doesNotBlock() {
        when(repository.findByCompanyIdAndYear(COMPANY_ID, 2026)).thenReturn(allMonthsClosedExceptDecember(2026));
        when(repository.findByCompanyIdAndYear(COMPANY_ID, 2025)).thenReturn(List.of());
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 12)).thenReturn(Optional.empty());
        when(journalEntryRepository.getYearEndBalancesByAccount(eq(COMPANY_ID), any(LocalDate.class), eq(true)))
                .thenReturn(List.of());
        when(journalEntryRepository.getYearEndBalancesByAccount(eq(COMPANY_ID), any(LocalDate.class), eq(false)))
                .thenReturn(List.of());
        stubClosingInfrastructure();

        AccountingPeriodResponseDTO result =
                service.closeYear(2026, "jaime", "annual close", GAIN_ACCOUNT_ID, LOSS_ACCOUNT_ID);

        assertThat(result.isYearClose()).isTrue();
    }

    // ============================================================
    // closeYear() -- Ganancia/Pérdida account validation
    // ============================================================

    @Test
    @DisplayName("closeYear() rejects when the Ganancia and Pérdida accounts are the same")
    void closeYear_sameGainAndLossAccount_throws() {
        when(repository.findByCompanyIdAndYear(COMPANY_ID, 2026)).thenReturn(allMonthsClosedExceptDecember(2026));
        when(accountRepository.findById(GAIN_ACCOUNT_ID))
                .thenReturn(Optional.of(equityAccount(GAIN_ACCOUNT_ID, "360501", false, false)));

        assertThatThrownBy(() -> service.closeYear(2026, "jaime", "x", GAIN_ACCOUNT_ID, GAIN_ACCOUNT_ID))
                .isInstanceOf(InvalidOperationException.class)
                .satisfies(ex -> assertThat(((InvalidOperationException) ex).getErrorCode())
                        .isEqualTo("GAIN_LOSS_ACCOUNTS_MUST_DIFFER"));
    }

    @Test
    @DisplayName("closeYear() rejects a Ganancia/Pérdida account that is not a posting account of class Patrimonio")
    void closeYear_invalidClosingAccountClass_throws() {
        when(repository.findByCompanyIdAndYear(COMPANY_ID, 2026)).thenReturn(allMonthsClosedExceptDecember(2026));
        ChartOfAccounts assetAccount = new ChartOfAccounts();
        assetAccount.setId(GAIN_ACCOUNT_ID);
        assetAccount.setCode("1105");
        assetAccount.setCompany(testCompany);
        assetAccount.setAccountClass(AccountClass.ASSET);
        assetAccount.setPostingAccount(true);
        when(accountRepository.findById(GAIN_ACCOUNT_ID)).thenReturn(Optional.of(assetAccount));

        assertThatThrownBy(() -> service.closeYear(2026, "jaime", "x", GAIN_ACCOUNT_ID, LOSS_ACCOUNT_ID))
                .isInstanceOf(InvalidOperationException.class)
                .satisfies(ex -> assertThat(((InvalidOperationException) ex).getErrorCode())
                        .isEqualTo("INVALID_CLOSING_ACCOUNT"));
    }

    @Test
    @DisplayName("closeYear() rejects a Ganancia/Pérdida account that itself requires Tercero or Centro de Costo")
    void closeYear_closingAccountRequiresDimension_throws() {
        when(repository.findByCompanyIdAndYear(COMPANY_ID, 2026)).thenReturn(allMonthsClosedExceptDecember(2026));
        when(accountRepository.findById(GAIN_ACCOUNT_ID))
                .thenReturn(Optional.of(equityAccount(GAIN_ACCOUNT_ID, "360501", true, false)));

        assertThatThrownBy(() -> service.closeYear(2026, "jaime", "x", GAIN_ACCOUNT_ID, LOSS_ACCOUNT_ID))
                .isInstanceOf(InvalidOperationException.class)
                .satisfies(ex -> assertThat(((InvalidOperationException) ex).getErrorCode())
                        .isEqualTo("CLOSING_ACCOUNT_CANNOT_REQUIRE_DIMENSIONS"));
    }

    // ============================================================
    // closeYear() -- third party / cost center data-integrity pre-check
    // ============================================================

    @Test
    @DisplayName("closeYear() rejects when a result account requiring Tercero has a movement with no tercero")
    void closeYear_resultAccountMissingRequiredThirdParty_throws() {
        when(repository.findByCompanyIdAndYear(COMPANY_ID, 2026)).thenReturn(allMonthsClosedExceptDecember(2026));
        when(accountRepository.findById(GAIN_ACCOUNT_ID))
                .thenReturn(Optional.of(equityAccount(GAIN_ACCOUNT_ID, "360501", false, false)));
        when(accountRepository.findById(LOSS_ACCOUNT_ID))
                .thenReturn(Optional.of(equityAccount(LOSS_ACCOUNT_ID, "360101", false, false)));

        ChartOfAccounts salesAccount = resultAccount(4001L, "413505", true, false);
        when(accountRepository.findByCompanyIdAndActiveTrueOrderByCodeAsc(COMPANY_ID))
                .thenReturn(List.of(salesAccount));

        // Row with no third party (index 1 = null) for an account that requires one.
        Object[] row = new Object[]{4001L, null, null, BigDecimal.ZERO, new BigDecimal("500.00")};
        when(journalEntryRepository.getYearEndBalancesByAccount(eq(COMPANY_ID), any(LocalDate.class), eq(true)))
                .thenReturn(List.<Object[]>of(row));

        assertThatThrownBy(() -> service.closeYear(2026, "jaime", "x", GAIN_ACCOUNT_ID, LOSS_ACCOUNT_ID))
                .isInstanceOf(InvalidOperationException.class)
                .satisfies(ex -> {
                    InvalidOperationException ioe = (InvalidOperationException) ex;
                    assertThat(ioe.getErrorCode()).isEqualTo("RESULT_ACCOUNTS_MISSING_THIRD_PARTY_OR_COST_CENTER");
                    assertThat(ioe.getMessage()).contains("413505");
                });

        verify(journalEntryRepository, never()).save(any(JournalEntry.class));
    }

    // ============================================================
    // closeYear() -- closing entry generation (happy paths)
    // ============================================================

    @Test
    @DisplayName("closeYear() generates a Ganancia closing entry when revenue exceeds expenses, and regenerates next year's opening balances")
    void closeYear_netGain_generatesClosingEntryCreditingGainAccount() {
        when(repository.findByCompanyIdAndYear(COMPANY_ID, 2026)).thenReturn(allMonthsClosedExceptDecember(2026));
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 12)).thenReturn(Optional.empty());
        stubClosingInfrastructure();

        // Revenue 413505: net credit balance of 1000 (debit 0, credit 1000).
        Object[] revenueRow = new Object[]{4001L, null, null, BigDecimal.ZERO, new BigDecimal("1000.00")};
        // Expense 513505: net debit balance of 400 (debit 400, credit 0).
        Object[] expenseRow = new Object[]{5001L, null, null, new BigDecimal("400.00"), BigDecimal.ZERO};
        when(journalEntryRepository.getYearEndBalancesByAccount(eq(COMPANY_ID), any(LocalDate.class), eq(true)))
                .thenReturn(List.of(revenueRow, expenseRow));
        when(journalEntryRepository.getYearEndBalancesByAccount(eq(COMPANY_ID), any(LocalDate.class), eq(false)))
                .thenReturn(List.of());
        when(entityManager.getReference(eq(ChartOfAccounts.class), any())).thenAnswer(invocation -> {
            ChartOfAccounts a = new ChartOfAccounts();
            a.setId(invocation.getArgument(1));
            return a;
        });

        AccountingPeriodResponseDTO result =
                service.closeYear(2026, "jaime", "annual close", GAIN_ACCOUNT_ID, LOSS_ACCOUNT_ID);

        ArgumentCaptor<JournalEntry> entryCaptor = ArgumentCaptor.forClass(JournalEntry.class);
        verify(journalEntryRepository).save(entryCaptor.capture());
        JournalEntry entry = entryCaptor.getValue();

        // 2 zeroing lines (revenue + expense) + 1 consolidated result line.
        assertThat(entry.getItems()).hasSize(3);
        BigDecimal totalDebit = entry.getItems().stream().map(i -> i.getDebit()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = entry.getItems().stream().map(i -> i.getCredit()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalDebit.compareTo(totalCredit)).isEqualTo(0); // always balanced

        boolean hasGainLine = entry.getItems().stream()
                .anyMatch(i -> i.getAccount().getId().equals(GAIN_ACCOUNT_ID)
                        && i.getCredit().compareTo(new BigDecimal("600.00")) == 0);
        assertThat(hasGainLine).isTrue();

        assertThat(result.isYearClose()).isTrue();
        assertThat(result.getGainAccountId()).isEqualTo(GAIN_ACCOUNT_ID);
        assertThat(result.getLossAccountId()).isEqualTo(LOSS_ACCOUNT_ID);
        assertThat(result.getClosingEntryId()).isNotNull();

        verify(openingBalanceRepository).deleteByCompanyIdAndYear(COMPANY_ID, 2027);
    }

    @Test
    @DisplayName("closeYear() generates a Pérdida closing entry debiting the loss account when expenses exceed revenue")
    void closeYear_netLoss_generatesClosingEntryDebitingLossAccount() {
        when(repository.findByCompanyIdAndYear(COMPANY_ID, 2026)).thenReturn(allMonthsClosedExceptDecember(2026));
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 12)).thenReturn(Optional.empty());
        stubClosingInfrastructure();

        Object[] revenueRow = new Object[]{4001L, null, null, BigDecimal.ZERO, new BigDecimal("200.00")};
        Object[] expenseRow = new Object[]{5001L, null, null, new BigDecimal("900.00"), BigDecimal.ZERO};
        when(journalEntryRepository.getYearEndBalancesByAccount(eq(COMPANY_ID), any(LocalDate.class), eq(true)))
                .thenReturn(List.of(revenueRow, expenseRow));
        when(journalEntryRepository.getYearEndBalancesByAccount(eq(COMPANY_ID), any(LocalDate.class), eq(false)))
                .thenReturn(List.of());
        when(entityManager.getReference(eq(ChartOfAccounts.class), any())).thenAnswer(invocation -> {
            ChartOfAccounts a = new ChartOfAccounts();
            a.setId(invocation.getArgument(1));
            return a;
        });

        service.closeYear(2026, "jaime", "annual close", GAIN_ACCOUNT_ID, LOSS_ACCOUNT_ID);

        ArgumentCaptor<JournalEntry> entryCaptor = ArgumentCaptor.forClass(JournalEntry.class);
        verify(journalEntryRepository).save(entryCaptor.capture());
        JournalEntry entry = entryCaptor.getValue();

        boolean hasLossLine = entry.getItems().stream()
                .anyMatch(i -> i.getAccount().getId().equals(LOSS_ACCOUNT_ID)
                        && i.getDebit().compareTo(new BigDecimal("700.00")) == 0);
        assertThat(hasLossLine).isTrue();
    }

    @Test
    @DisplayName("closeYear() posts no closing entry when there is no Income Statement activity at all")
    void closeYear_noActivity_generatesNoClosingEntry() {
        when(repository.findByCompanyIdAndYear(COMPANY_ID, 2026)).thenReturn(allMonthsClosedExceptDecember(2026));
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 12)).thenReturn(Optional.empty());
        stubClosingInfrastructure();
        when(journalEntryRepository.getYearEndBalancesByAccount(eq(COMPANY_ID), any(LocalDate.class), eq(true)))
                .thenReturn(List.of());
        when(journalEntryRepository.getYearEndBalancesByAccount(eq(COMPANY_ID), any(LocalDate.class), eq(false)))
                .thenReturn(List.of());

        AccountingPeriodResponseDTO result =
                service.closeYear(2026, "jaime", "annual close", GAIN_ACCOUNT_ID, LOSS_ACCOUNT_ID);

        verify(journalEntryRepository, never()).save(any(JournalEntry.class));
        assertThat(result.getClosingEntryId()).isNull();
        assertThat(result.isYearClose()).isTrue();
    }

    // ============================================================
    // reopenPeriod() / reopenYear()
    // ============================================================

    @Test
    @DisplayName("reopenPeriod() reopens an existing closed period and records the audit fields")
    void reopenPeriod_reopensExistingPeriod() {
        AccountingPeriod existing = existingPeriod(2026, 3, false);
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 3)).thenReturn(Optional.of(existing));

        AccountingPeriodResponseDTO result = service.reopenPeriod(2026, 3, "jaime", "correction needed");

        assertThat(existing.isOpen()).isTrue();
        assertThat(existing.isYearClose()).isFalse();
        assertThat(existing.getReopenedBy()).isEqualTo("jaime");
        assertThat(existing.getReopeningNotes()).isEqualTo("correction needed");
        assertThat(existing.getReopenedAt()).isNotNull();
        assertThat(result.isOpen()).isTrue();

        // FIX regression guard: same DTO-mapping gap as closePeriod() above.
        assertThat(result.getReopenedBy()).isEqualTo("jaime");
        assertThat(result.getReopeningNotes()).isEqualTo("correction needed");
        assertThat(result.getReopenedAt()).isNotNull();
    }

    @Test
    @DisplayName("reopenPeriod() rejects a period that was never created")
    void reopenPeriod_nonExistentPeriod_throwsResourceNotFound() {
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 3)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reopenPeriod(2026, 3, "jaime", "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("reopenYear() clears the year-close flag only on periods that had it set, when next year does not exist")
    void reopenYear_clearsYearCloseOnlyOnFlaggedPeriods() {
        AccountingPeriod december = existingPeriod(2026, 12, false);
        december.setYearClose(true);
        AccountingPeriod june = existingPeriod(2026, 6, false); // closed individually, but not year-sealed
        june.setYearClose(false);

        when(repository.findByCompanyIdAndYear(COMPANY_ID, 2026)).thenReturn(List.of(december, june));
        when(repository.findByCompanyIdAndYear(COMPANY_ID, 2027)).thenReturn(List.of());

        service.reopenYear(2026, "jaime", "unseal for audit adjustment");

        assertThat(december.isYearClose()).isFalse();
        assertThat(december.getReopenedBy()).isEqualTo("jaime");
        assertThat(december.getReopeningNotes()).contains("unseal for audit adjustment");
        // June was never year-sealed -- reopenYear must not touch its audit fields.
        assertThat(june.isYearClose()).isFalse();
        assertThat(june.getReopenedBy()).isNull();
        // June's individual open/closed state is untouched by a year-level unseal.
        assertThat(june.isOpen()).isFalse();
    }

    @Test
    @DisplayName("reopenYear() rejects reopening year N while year N+1 is still closed")
    void reopenYear_nextYearStillClosed_throws() {
        AccountingPeriod december2026 = existingPeriod(2026, 12, false);
        december2026.setYearClose(true);
        when(repository.findByCompanyIdAndYear(COMPANY_ID, 2026)).thenReturn(List.of(december2026));

        AccountingPeriod december2027 = existingPeriod(2027, 12, false);
        december2027.setYearClose(true); // 2027 is still sealed
        when(repository.findByCompanyIdAndYear(COMPANY_ID, 2027)).thenReturn(List.of(december2027));

        assertThatThrownBy(() -> service.reopenYear(2026, "jaime", "correction"))
                .isInstanceOf(InvalidOperationException.class)
                .satisfies(ex -> assertThat(((InvalidOperationException) ex).getErrorCode())
                        .isEqualTo("NEXT_YEAR_MUST_BE_OPEN_BEFORE_REOPEN"));

        // Must never touch 2026's own periods when blocked.
        verify(repository, never()).saveAll(any());
    }

    @Test
    @DisplayName("reopenYear() succeeds when year N+1 exists but is already open")
    void reopenYear_nextYearOpen_succeeds() {
        AccountingPeriod december2026 = existingPeriod(2026, 12, false);
        december2026.setYearClose(true);
        when(repository.findByCompanyIdAndYear(COMPANY_ID, 2026)).thenReturn(List.of(december2026));

        AccountingPeriod december2027 = existingPeriod(2027, 12, false);
        december2027.setYearClose(false); // 2027 already reopened
        when(repository.findByCompanyIdAndYear(COMPANY_ID, 2027)).thenReturn(List.of(december2027));

        service.reopenYear(2026, "jaime", "correction");

        assertThat(december2026.isYearClose()).isFalse();
    }

    // ============================================================
    // validateDateIsOpen() -- called by JournalEntryService before posting
    // ============================================================

    @Test
    @DisplayName("validateDateIsOpen() rejects a null date")
    void validateDateIsOpen_nullDate_throws() {
        assertThatThrownBy(() -> service.validateDateIsOpen(null, COMPANY_ID))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    @DisplayName("validateDateIsOpen() rejects any date in a year-closed fiscal year, even if that month's own record is open")
    void validateDateIsOpen_yearClosed_blanketBlocksEveryMonth() {
        when(repository.existsByCompanyIdAndYearAndYearCloseTrue(COMPANY_ID, 2026)).thenReturn(true);

        assertThatThrownBy(() -> service.validateDateIsOpen(LocalDate.of(2026, 3, 15), COMPANY_ID))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("2026")
                .hasMessageContaining("CLOSED");

        // The blanket year lock short-circuits -- the per-month lookup should
        // not even be needed to reach the correct (rejecting) outcome.
    }

    @Test
    @DisplayName("validateDateIsOpen() rejects a date in an individually-closed month")
    void validateDateIsOpen_monthClosed_throws() {
        when(repository.existsByCompanyIdAndYearAndYearCloseTrue(COMPANY_ID, 2026)).thenReturn(false);
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 3))
                .thenReturn(Optional.of(existingPeriod(2026, 3, false)));

        assertThatThrownBy(() -> service.validateDateIsOpen(LocalDate.of(2026, 3, 15), COMPANY_ID))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("CLOSED");
    }

    @Test
    @DisplayName("validateDateIsOpen() allows a date in an open month")
    void validateDateIsOpen_monthOpen_succeeds() {
        when(repository.existsByCompanyIdAndYearAndYearCloseTrue(COMPANY_ID, 2026)).thenReturn(false);
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 3))
                .thenReturn(Optional.of(existingPeriod(2026, 3, true)));

        service.validateDateIsOpen(LocalDate.of(2026, 3, 15), COMPANY_ID); // no exception
    }

    @Test
    @DisplayName("validateDateIsOpen() allows a date in a month with no period record yet (defaults to open)")
    void validateDateIsOpen_noPeriodRecord_defaultsToOpen() {
        when(repository.existsByCompanyIdAndYearAndYearCloseTrue(COMPANY_ID, 2026)).thenReturn(false);
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 3)).thenReturn(Optional.empty());

        service.validateDateIsOpen(LocalDate.of(2026, 3, 15), COMPANY_ID); // no exception
    }

    // ============================================================
    // isPeriodClosed()
    // ============================================================

    @Test
    @DisplayName("isPeriodClosed() returns true when the fiscal year is sealed, regardless of the month record")
    void isPeriodClosed_yearClosed_returnsTrue() {
        when(repository.existsByCompanyIdAndYearAndYearCloseTrue(COMPANY_ID, 2026)).thenReturn(true);

        assertThat(service.isPeriodClosed(2026, 3)).isTrue();
    }

    @Test
    @DisplayName("isPeriodClosed() returns true when the individual month is closed")
    void isPeriodClosed_monthClosedIndividually_returnsTrue() {
        when(repository.existsByCompanyIdAndYearAndYearCloseTrue(COMPANY_ID, 2026)).thenReturn(false);
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 3))
                .thenReturn(Optional.of(existingPeriod(2026, 3, false)));

        assertThat(service.isPeriodClosed(2026, 3)).isTrue();
    }

    @Test
    @DisplayName("isPeriodClosed() returns false for an open month")
    void isPeriodClosed_monthOpen_returnsFalse() {
        when(repository.existsByCompanyIdAndYearAndYearCloseTrue(COMPANY_ID, 2026)).thenReturn(false);
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 3))
                .thenReturn(Optional.of(existingPeriod(2026, 3, true)));

        assertThat(service.isPeriodClosed(2026, 3)).isFalse();
    }

    @Test
    @DisplayName("isPeriodClosed() returns false when no period record exists at all")
    void isPeriodClosed_noRecord_returnsFalse() {
        when(repository.existsByCompanyIdAndYearAndYearCloseTrue(COMPANY_ID, 2026)).thenReturn(false);
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 3)).thenReturn(Optional.empty());

        assertThat(service.isPeriodClosed(2026, 3)).isFalse();
    }
}
