package com.erp.erp_cloud.service.reports.financial;

import com.erp.erp_cloud.dto.reports.financial.BalanceSheetReport;
import com.erp.erp_cloud.dto.reports.financial.BalanceSheetSection;
import com.erp.erp_cloud.dto.reports.financial.IncomeStatementDetailedReport;
import com.erp.erp_cloud.dto.reports.financial.IncomeStatementLineDetailed;
import com.erp.erp_cloud.dto.reports.financial.IncomeStatementReport;
import com.erp.erp_cloud.dto.reports.financial.IncomeStatementSection;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.enums.AccountCategory;
import com.erp.erp_cloud.enums.AccountClass;
import com.erp.erp_cloud.enums.FinancialStatement;
import com.erp.erp_cloud.repository.AccountingPeriodRepository;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.security.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the 2026-10-10 bug fix: a Balance Sheet section used to ADD every
 * account's balance (after forcing it positive), instead of NETTING
 * accounts whose own nature opposes their class's normal polarity -- e.g.
 * "IVA Descontable" (nature D, a contra-liability) sitting inside Pasivos
 * (normally nature C) next to "IVA Generado" (nature C). The user caught
 * this comparing the Balance General against the Balance de Comprobación
 * Detallado and the legacy SIEWIN report, both of which correctly net the
 * two sub-accounts -- see FinancialStatementService for the full story.
 */
class FinancialStatementServiceTest {

    private static final Long COMPANY_ID = 1L;
    private static final LocalDate AS_OF_DATE = LocalDate.of(2025, 12, 31);

    @Mock
    private JournalEntryRepository journalEntryRepository;

    @Mock
    private ChartOfAccountsRepository chartOfAccountsRepository;

    @Mock
    private AccountingPeriodRepository accountingPeriodRepository;

    private FinancialStatementService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new FinancialStatementService(journalEntryRepository, chartOfAccountsRepository, accountingPeriodRepository);

        Company company = new Company();
        company.setId(COMPANY_ID);
        company.setLegalName("EMPRESA DE PRUEBA S.A.S.");
        TenantContext.setContext(company);

        // No accounts for Asset/Equity classes in this scenario -- only
        // Pasivos (Liability) is under test.
        when(chartOfAccountsRepository.getAccountsForBalanceSheet(eq(COMPANY_ID), eq(AccountClass.ASSET)))
                .thenReturn(List.of());
        when(chartOfAccountsRepository.getAccountsForBalanceSheet(eq(COMPANY_ID), eq(AccountClass.EQUITY)))
                .thenReturn(List.of());

        // No accounts for Cost/Expense classes unless a test overrides it --
        // only Revenue is under test for the Income Statement scenarios.
        when(chartOfAccountsRepository.getAccountsForIncomeStatement(
                eq(COMPANY_ID), eq(AccountClass.COST), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(chartOfAccountsRepository.getAccountsForIncomeStatement(
                eq(COMPANY_ID), eq(AccountClass.EXPENSE), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(chartOfAccountsRepository.getAccountsForIncomeStatement(
                eq(COMPANY_ID), eq(AccountClass.REVENUE), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // Builds a ChartOfAccounts fixture for the hierarchical
    // getIncomeStatementDetailed() tests below -- same helper shape as
    // JournalEntryServiceTest.hierarchyAccount(), needed to exercise the
    // level/parent-chain rollup (getAccountClassDisplay()-style switches
    // elsewhere NPE on a null accountClass, so it's always set here).
    private ChartOfAccounts hierarchyAccount(String code, String name, int level, ChartOfAccounts parent,
                                              AccountClass accountClass, boolean postingAccount) {
        ChartOfAccounts a = new ChartOfAccounts();
        a.setCode(code);
        a.setName(name);
        a.setActive(true);
        a.setLevel((byte) level);
        a.setParent(parent);
        a.setAccountClass(accountClass);
        a.setPostingAccount(postingAccount);
        a.setClosesAtYearEnd(true);
        // isIncomeStatementAccount()/isBalanceSheetAccount() switch on
        // financialStatement, NOT accountClass -- normally kept in sync
        // by the entity's own @PrePersist/@PreUpdate callback, which
        // never fires on a plain `new` object in a unit test, so it has
        // to be set here explicitly or getIncomeStatementDetailed()
        // would skip every fixture account as a Balance Sheet account.
        a.setFinancialStatement(
                (accountClass == AccountClass.REVENUE
                        || accountClass == AccountClass.EXPENSE
                        || accountClass == AccountClass.COST)
                        ? FinancialStatement.INCOME_STATEMENT
                        : FinancialStatement.BALANCE_SHEET);
        return a;
    }

    @Test
    @DisplayName("Pasivos nets a contra-liability (IVA Descontable, nature D) against a normal liability (IVA Generado, nature C) instead of adding them")
    void getBalanceSheet_netsContraLiabilityAgainstNormalLiabilityWithinSameCategory() {
        // IVA Generado del 19%: nature C, credit balance of 2,280,000 -- a real liability.
        // IVA Descontable por Compras del 19%: nature D, debit balance of 957,983 --
        // a contra-liability that should REDUCE the net amount owed, exactly like it
        // does in the Balance de Comprobación Detallado and the legacy SIEWIN report.
        when(journalEntryRepository.getAccountBalancesAsOfDate(eq(COMPANY_ID), eq(AS_OF_DATE)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"240805", "C", BigDecimal.ZERO, new BigDecimal("2280000.00")},
                        new Object[]{"240810", "D", new BigDecimal("957983.00"), BigDecimal.ZERO}
                ));

        when(chartOfAccountsRepository.getAccountsForBalanceSheet(eq(COMPANY_ID), eq(AccountClass.LIABILITY)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"240805", "IVA Generado del 19%", AccountCategory.TAXES_PAYABLE, 1},
                        new Object[]{"240810", "IVA Descontable por Compras del 19%", AccountCategory.TAXES_PAYABLE, 2}
                ));

        BalanceSheetReport report = service.getBalanceSheet(AS_OF_DATE);

        BigDecimal expectedNet = new BigDecimal("2280000.00").subtract(new BigDecimal("957983.00"));

        assertThat(report.getTotalLiabilities()).isEqualByComparingTo(expectedNet);

        BalanceSheetSection taxesSection = report.getLiabilitySections().stream()
                .filter(s -> s.getSectionNameEs().equals(AccountCategory.TAXES_PAYABLE.getDisplayNameEs()))
                .findFirst()
                .orElseThrow();
        assertThat(taxesSection.getSectionTotal()).isEqualByComparingTo(expectedNet);

        // Both lines still display as positive magnitudes -- only the
        // section total changed, not the per-line presentation.
        assertThat(taxesSection.getAccountLines())
                .extracting(BalanceSheetSection.AccountLine::getBalance)
                .containsExactlyInAnyOrder(
                        new BigDecimal("2280000.00"),
                        new BigDecimal("957983.00")
                );
    }

    @Test
    @DisplayName("A section with only normal-nature accounts is unaffected by the fix (simple sum, same as before)")
    void getBalanceSheet_normalLiabilityAccountsOnly_sumsAsBefore() {
        when(journalEntryRepository.getAccountBalancesAsOfDate(eq(COMPANY_ID), eq(AS_OF_DATE)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"220505", "C", BigDecimal.ZERO, new BigDecimal("1000000.00")},
                        new Object[]{"220510", "C", BigDecimal.ZERO, new BigDecimal("500000.00")}
                ));

        when(chartOfAccountsRepository.getAccountsForBalanceSheet(eq(COMPANY_ID), eq(AccountClass.LIABILITY)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"220505", "Proveedores Nacionales", AccountCategory.ACCOUNTS_PAYABLE, 1},
                        new Object[]{"220510", "Proveedores del Exterior", AccountCategory.ACCOUNTS_PAYABLE, 2}
                ));

        BalanceSheetReport report = service.getBalanceSheet(AS_OF_DATE);

        assertThat(report.getTotalLiabilities()).isEqualByComparingTo(new BigDecimal("1500000.00"));
    }

    @Test
    @DisplayName("Income Statement nets a contra-revenue account (Devoluciones en Ventas, nature D) against normal sales revenue (nature C) instead of adding them")
    void getIncomeStatement_netsContraRevenueAgainstNormalRevenueWithinSameCategory() {
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2025, 12, 31);

        // Ventas: nature C, credit-heavy -- real revenue of 10,000,000.
        // Devoluciones en Ventas: nature D, debit-heavy -- a contra-revenue
        // account that should REDUCE net revenue, not add to it.
        when(chartOfAccountsRepository.getAccountsForIncomeStatement(
                eq(COMPANY_ID), eq(AccountClass.REVENUE), eq(startDate), eq(endDate)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"413505", "Ventas", AccountCategory.SALES_REVENUE, 1,
                                new BigDecimal("-10000000.00")}, // raw debit-credit: all credit
                        new Object[]{"413595", "Devoluciones en Ventas", AccountCategory.SALES_REVENUE, 2,
                                new BigDecimal("600000.00")} // raw debit-credit: all debit
                ));

        IncomeStatementReport report = service.getIncomeStatement(startDate, endDate);

        BigDecimal expectedNet = new BigDecimal("10000000.00").subtract(new BigDecimal("600000.00"));

        assertThat(report.getTotalRevenue()).isEqualByComparingTo(expectedNet);

        IncomeStatementSection salesSection = report.getRevenueSections().stream()
                .filter(s -> s.getSectionNameEs().equals(AccountCategory.SALES_REVENUE.getDisplayNameEs()))
                .findFirst()
                .orElseThrow();
        assertThat(salesSection.getSectionTotal()).isEqualByComparingTo(expectedNet);

        // Both lines still display as positive magnitudes.
        assertThat(salesSection.getAccountLines())
                .extracting(IncomeStatementSection.AccountLine::getAmount)
                .containsExactlyInAnyOrder(
                        new BigDecimal("10000000.00"),
                        new BigDecimal("600000.00")
                );
    }

    // ═══════════════════════════════════════════════════════════
    // 2026-10-12: computed "Resultado del Ejercicio (sin cerrar)" line
    // ═══════════════════════════════════════════════════════════
    //
    // Reported by the user: the Balance General flagged "Fuera de
    // Balance" for a $2,000,000 difference (Ingresos $12,000,000 -
    // Gastos $10,000,000) that was simply the current year's net income
    // not yet closed into Patrimonio -- not a real descuadre. These
    // tests cover appendUnclosedCurrentYearResult(): it adds a computed
    // line to Patrimonio while the year is open, skips it once the year
    // is closed (the closing entry already moved it to a real account),
    // and labels a loss differently from a profit.

    @Test
    @DisplayName("Open fiscal year: adds a computed 'Utilidad del Ejercicio (sin cerrar)' line to Patrimonio for Ingresos $12,000,000 - Gastos $10,000,000")
    void getBalanceSheet_openYear_addsUnclosedProfitToEquity() {
        LocalDate yearStart = LocalDate.of(AS_OF_DATE.getYear(), 1, 1);

        // accountingPeriodRepository is left unstubbed on purpose here --
        // Mockito's default `false` means "year not closed", which is
        // exactly the scenario under test.
        when(chartOfAccountsRepository.getAccountsForIncomeStatement(
                eq(COMPANY_ID), eq(AccountClass.REVENUE), eq(yearStart), eq(AS_OF_DATE)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"413505", "Ventas", AccountCategory.SALES_REVENUE, 1,
                                new BigDecimal("-12000000.00")} // raw debit-credit: all credit
                ));
        when(chartOfAccountsRepository.getAccountsForIncomeStatement(
                eq(COMPANY_ID), eq(AccountClass.EXPENSE), eq(yearStart), eq(AS_OF_DATE)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"519505", "Gastos de Personal", AccountCategory.ADMINISTRATIVE_EXPENSE, 1,
                                new BigDecimal("10000000.00")} // raw debit-credit: all debit
                ));

        when(journalEntryRepository.getAccountBalancesAsOfDate(eq(COMPANY_ID), eq(AS_OF_DATE)))
                .thenReturn(List.of());
        when(chartOfAccountsRepository.getAccountsForBalanceSheet(eq(COMPANY_ID), eq(AccountClass.LIABILITY)))
                .thenReturn(List.of());

        BalanceSheetReport report = service.getBalanceSheet(AS_OF_DATE);

        BigDecimal expectedNetIncome = new BigDecimal("2000000.00");
        assertThat(report.getTotalEquity()).isEqualByComparingTo(expectedNetIncome);

        BalanceSheetSection resultSection = report.getEquitySections().stream()
                .filter(s -> s.getSectionNameEs().equals("Utilidad del Ejercicio (sin cerrar)"))
                .findFirst()
                .orElseThrow();
        assertThat(resultSection.getSectionTotal()).isEqualByComparingTo(expectedNetIncome);
        assertThat(resultSection.getAccountLines()).hasSize(1);
        assertThat(resultSection.getAccountLines().get(0).getBalance()).isEqualByComparingTo(expectedNetIncome);
    }

    @Test
    @DisplayName("Open fiscal year with a net loss: labels the line 'Pérdida del Ejercicio (sin cerrar)' with a negative section total")
    void getBalanceSheet_openYear_addsUnclosedLossToEquity() {
        LocalDate yearStart = LocalDate.of(AS_OF_DATE.getYear(), 1, 1);

        when(chartOfAccountsRepository.getAccountsForIncomeStatement(
                eq(COMPANY_ID), eq(AccountClass.REVENUE), eq(yearStart), eq(AS_OF_DATE)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"413505", "Ventas", AccountCategory.SALES_REVENUE, 1,
                                new BigDecimal("-5000000.00")}
                ));
        when(chartOfAccountsRepository.getAccountsForIncomeStatement(
                eq(COMPANY_ID), eq(AccountClass.EXPENSE), eq(yearStart), eq(AS_OF_DATE)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"519505", "Gastos de Personal", AccountCategory.ADMINISTRATIVE_EXPENSE, 1,
                                new BigDecimal("8000000.00")}
                ));

        when(journalEntryRepository.getAccountBalancesAsOfDate(eq(COMPANY_ID), eq(AS_OF_DATE)))
                .thenReturn(List.of());
        when(chartOfAccountsRepository.getAccountsForBalanceSheet(eq(COMPANY_ID), eq(AccountClass.LIABILITY)))
                .thenReturn(List.of());

        BalanceSheetReport report = service.getBalanceSheet(AS_OF_DATE);

        BigDecimal expectedNetLoss = new BigDecimal("-3000000.00");
        assertThat(report.getTotalEquity()).isEqualByComparingTo(expectedNetLoss);

        BalanceSheetSection resultSection = report.getEquitySections().stream()
                .filter(s -> s.getSectionNameEs().equals("Pérdida del Ejercicio (sin cerrar)"))
                .findFirst()
                .orElseThrow();
        assertThat(resultSection.getSectionTotal()).isEqualByComparingTo(expectedNetLoss);
        assertThat(resultSection.getAccountLines().get(0).getBalance()).isEqualByComparingTo(new BigDecimal("3000000.00"));
    }

    @Test
    @DisplayName("Closed fiscal year: never adds the computed result line, even with non-zero Ingresos/Gastos -- the closing entry already moved it to a real Patrimonio account")
    void getBalanceSheet_closedYear_doesNotAddResultLine() {
        when(accountingPeriodRepository.existsByCompanyIdAndYearAndYearCloseTrue(eq(COMPANY_ID), eq(AS_OF_DATE.getYear())))
                .thenReturn(true);

        LocalDate yearStart = LocalDate.of(AS_OF_DATE.getYear(), 1, 1);
        when(chartOfAccountsRepository.getAccountsForIncomeStatement(
                eq(COMPANY_ID), eq(AccountClass.REVENUE), eq(yearStart), eq(AS_OF_DATE)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"413505", "Ventas", AccountCategory.SALES_REVENUE, 1,
                                new BigDecimal("-12000000.00")}
                ));

        when(journalEntryRepository.getAccountBalancesAsOfDate(eq(COMPANY_ID), eq(AS_OF_DATE)))
                .thenReturn(List.of());
        when(chartOfAccountsRepository.getAccountsForBalanceSheet(eq(COMPANY_ID), eq(AccountClass.LIABILITY)))
                .thenReturn(List.of());

        BalanceSheetReport report = service.getBalanceSheet(AS_OF_DATE);

        assertThat(report.getTotalEquity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(report.getEquitySections()).noneMatch(
                s -> s.getSectionNameEs() != null && s.getSectionNameEs().contains("Ejercicio"));
    }

    // ═══════════════════════════════════════════════════════════
    // 2026-10-12: getIncomeStatementDetailed() -- hierarchical Estado
    // de Resultados requested by the user after closing fiscal year
    // 2025, modeled after their legacy "PERDIDA O GANANCIA" report.
    // Mirrors the rollup/double-counting coverage already proven for
    // getTrialBalanceDetailed() (JournalEntryServiceTest), scoped to
    // Ingresos/Gastos/Costos and the dynamic Utilidad/Pérdida label.
    // ═══════════════════════════════════════════════════════════

    @Test
    @DisplayName("getIncomeStatementDetailed() rolls up header accounts' Ingresos totals from their leaf descendants, without double-counting totalRevenue")
    void getIncomeStatementDetailed_rollsUpHeaderTotalsFromLeafDescendants() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 12, 31);

        // 4 (class header) -> 41 (group header) -> 413505 / 413510 (two leaves)
        ChartOfAccounts classHeader = hierarchyAccount("4", "Ingresos", 1, null, AccountClass.REVENUE, false);
        ChartOfAccounts groupHeader = hierarchyAccount("41", "Operacionales", 2, classHeader, AccountClass.REVENUE, false);
        ChartOfAccounts leaf1 = hierarchyAccount("413505", "Ventas", 3, groupHeader, AccountClass.REVENUE, true);
        ChartOfAccounts leaf2 = hierarchyAccount("413510", "Otros Ingresos Operacionales", 3, groupHeader, AccountClass.REVENUE, true);

        when(chartOfAccountsRepository.findByCompanyIdAndActiveTrueOrderByCodeAsc(COMPANY_ID))
                .thenReturn(List.of(classHeader, groupHeader, leaf1, leaf2));
        when(chartOfAccountsRepository.getPeriodActivityExcludingClosingEntries(eq(COMPANY_ID), eq(start), eq(end)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"413505", "Ventas", AccountClass.REVENUE, true,
                                BigDecimal.ZERO, new BigDecimal("5000000.00")}, // all credit
                        new Object[]{"413510", "Otros Ingresos Operacionales", AccountClass.REVENUE, true,
                                BigDecimal.ZERO, new BigDecimal("2000000.00")} // all credit
                ));

        IncomeStatementDetailedReport report = service.getIncomeStatementDetailed(start, end);

        Map<String, IncomeStatementLineDetailed> byCode = report.getRevenueLines().stream()
                .collect(Collectors.toMap(IncomeStatementLineDetailed::getAccountCode, line -> line));
        assertThat(byCode).containsKeys("4", "41", "413505", "413510");

        // Both header levels show the SUM of the two leaves (7,000,000),
        // not just their own (nonexistent) direct postings.
        assertThat(byCode.get("4").getAmount()).isEqualByComparingTo("7000000.00");
        assertThat(byCode.get("4").isPostingAccount()).isFalse();
        assertThat(byCode.get("41").getAmount()).isEqualByComparingTo("7000000.00");
        assertThat(byCode.get("41").isPostingAccount()).isFalse();
        assertThat(byCode.get("413505").getAmount()).isEqualByComparingTo("5000000.00");
        assertThat(byCode.get("413510").getAmount()).isEqualByComparingTo("2000000.00");

        // Grand total counts each LEAF once -- not tripled by also
        // summing the two header rows carrying the same rolled-up number.
        assertThat(report.getTotalRevenue()).isEqualByComparingTo("7000000.00");
    }

    @Test
    @DisplayName("getIncomeStatementDetailed() nets a contra-revenue account (Devoluciones en Ventas, nature D) against normal sales revenue within the same parent group")
    void getIncomeStatementDetailed_netsContraRevenueWithinSameParentGroup() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 12, 31);

        ChartOfAccounts groupHeader = hierarchyAccount("4135", "Comercio al por Mayor", 2, null, AccountClass.REVENUE, false);
        ChartOfAccounts ventas = hierarchyAccount("413505", "Ventas", 3, groupHeader, AccountClass.REVENUE, true);
        ChartOfAccounts devoluciones = hierarchyAccount("413595", "Devoluciones en Ventas", 3, groupHeader, AccountClass.REVENUE, true);

        when(chartOfAccountsRepository.findByCompanyIdAndActiveTrueOrderByCodeAsc(COMPANY_ID))
                .thenReturn(List.of(groupHeader, ventas, devoluciones));
        when(chartOfAccountsRepository.getPeriodActivityExcludingClosingEntries(eq(COMPANY_ID), eq(start), eq(end)))
                .thenReturn(List.<Object[]>of(
                        // Ventas: all credit, real revenue of 10,000,000.
                        new Object[]{"413505", "Ventas", AccountClass.REVENUE, true,
                                BigDecimal.ZERO, new BigDecimal("10000000.00")},
                        // Devoluciones en Ventas: nature D, all debit -- a
                        // contra-revenue account that should REDUCE the
                        // group's net, not add to it.
                        new Object[]{"413595", "Devoluciones en Ventas", AccountClass.REVENUE, true,
                                new BigDecimal("600000.00"), BigDecimal.ZERO}
                ));

        IncomeStatementDetailedReport report = service.getIncomeStatementDetailed(start, end);

        BigDecimal expectedNet = new BigDecimal("10000000.00").subtract(new BigDecimal("600000.00"));

        Map<String, IncomeStatementLineDetailed> byCode = report.getRevenueLines().stream()
                .collect(Collectors.toMap(IncomeStatementLineDetailed::getAccountCode, line -> line));
        assertThat(byCode.get("4135").getAmount()).isEqualByComparingTo(expectedNet);
        assertThat(report.getTotalRevenue()).isEqualByComparingTo(expectedNet);

        // Ventas (normal nature for a credit-normal section) displays as
        // a positive magnitude. Devoluciones en Ventas is a CONTRA
        // account -- its own line is oriented by the section's class
        // (REVENUE => negate(raw)), never by its own nature, same as
        // every other line here (including the header above). Its raw
        // debit-heavy balance (+600,000) therefore negates to -600,000
        // on its own line, which is exactly what IncomeStatementLineDetailed.amount's
        // javadoc documents ("a negative amount here means a
        // contra-subtree outweighs its own section") and what
        // getFormattedAmount() renders in parentheses -- this is NOT the
        // old flat IncomeStatementSection.AccountLine convention (always
        // a positive display magnitude), which this hierarchical report
        // deliberately does not replicate.
        assertThat(byCode.get("413505").getAmount()).isEqualByComparingTo("10000000.00");
        assertThat(byCode.get("413595").getAmount()).isEqualByComparingTo("-600000.00");
    }

    @Test
    @DisplayName("getIncomeStatementDetailed() omits an active account with zero period activity, even if it appears in the chart of accounts")
    void getIncomeStatementDetailed_omitsZeroActivityAccounts() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 12, 31);

        ChartOfAccounts used = hierarchyAccount("413505", "Ventas", 1, null, AccountClass.REVENUE, true);
        ChartOfAccounts unused = hierarchyAccount("419595", "Otros Ingresos Diversos", 1, null, AccountClass.REVENUE, true);

        when(chartOfAccountsRepository.findByCompanyIdAndActiveTrueOrderByCodeAsc(COMPANY_ID))
                .thenReturn(List.of(used, unused));
        when(chartOfAccountsRepository.getPeriodActivityExcludingClosingEntries(eq(COMPANY_ID), eq(start), eq(end)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"413505", "Ventas", AccountClass.REVENUE, true,
                                BigDecimal.ZERO, new BigDecimal("1000000.00")}
                        // "419595" has no postings at all in this period --
                        // never appears in getPeriodActivity()'s result.
                ));

        IncomeStatementDetailedReport report = service.getIncomeStatementDetailed(start, end);

        assertThat(report.getRevenueLines())
                .extracting(IncomeStatementLineDetailed::getAccountCode)
                .containsExactly("413505");
        assertThat(report.getTotalRevenue()).isEqualByComparingTo("1000000.00");
    }

    @Test
    @DisplayName("getIncomeStatementDetailed() labels a positive result 'Utilidad antes de Impuestos' / 'Profit Before Taxes'")
    void getIncomeStatementDetailed_profitCase_labelsUtilidadAntesDeImpuestos() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 12, 31);

        ChartOfAccounts revenue = hierarchyAccount("413505", "Ventas", 1, null, AccountClass.REVENUE, true);
        ChartOfAccounts expense = hierarchyAccount("519505", "Gastos de Personal", 1, null, AccountClass.EXPENSE, true);

        when(chartOfAccountsRepository.findByCompanyIdAndActiveTrueOrderByCodeAsc(COMPANY_ID))
                .thenReturn(List.of(revenue, expense));
        when(chartOfAccountsRepository.getPeriodActivityExcludingClosingEntries(eq(COMPANY_ID), eq(start), eq(end)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"413505", "Ventas", AccountClass.REVENUE, true,
                                BigDecimal.ZERO, new BigDecimal("12000000.00")}, // all credit
                        new Object[]{"519505", "Gastos de Personal", AccountClass.EXPENSE, true,
                                new BigDecimal("10000000.00"), BigDecimal.ZERO} // all debit
                ));

        IncomeStatementDetailedReport report = service.getIncomeStatementDetailed(start, end);

        assertThat(report.getNetResultBeforeTaxes()).isEqualByComparingTo("2000000.00");
        assertThat(report.isProfitable()).isTrue();
        assertThat(report.getResultLabel()).isEqualTo("Profit Before Taxes");
        assertThat(report.getResultLabelEs()).isEqualTo("Utilidad antes de Impuestos");
        assertThat(report.getNetResultMagnitude()).isEqualByComparingTo("2000000.00");
    }

    @Test
    @DisplayName("getIncomeStatementDetailed() labels a negative result 'Pérdida antes de Impuestos' / 'Loss Before Taxes', including Costos in the calculation")
    void getIncomeStatementDetailed_lossCase_labelsPerdidaAntesDeImpuestos() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 12, 31);

        ChartOfAccounts revenue = hierarchyAccount("413505", "Ventas", 1, null, AccountClass.REVENUE, true);
        ChartOfAccounts expense = hierarchyAccount("519505", "Gastos de Personal", 1, null, AccountClass.EXPENSE, true);
        ChartOfAccounts cost = hierarchyAccount("613505", "Costo de Ventas", 1, null, AccountClass.COST, true);

        when(chartOfAccountsRepository.findByCompanyIdAndActiveTrueOrderByCodeAsc(COMPANY_ID))
                .thenReturn(List.of(revenue, expense, cost));
        when(chartOfAccountsRepository.getPeriodActivityExcludingClosingEntries(eq(COMPANY_ID), eq(start), eq(end)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"413505", "Ventas", AccountClass.REVENUE, true,
                                BigDecimal.ZERO, new BigDecimal("5000000.00")}, // all credit
                        new Object[]{"519505", "Gastos de Personal", AccountClass.EXPENSE, true,
                                new BigDecimal("3000000.00"), BigDecimal.ZERO}, // all debit
                        new Object[]{"613505", "Costo de Ventas", AccountClass.COST, true,
                                new BigDecimal("4000000.00"), BigDecimal.ZERO} // all debit
                ));

        IncomeStatementDetailedReport report = service.getIncomeStatementDetailed(start, end);

        // 5,000,000 - 3,000,000 - 4,000,000 = -2,000,000
        assertThat(report.getNetResultBeforeTaxes()).isEqualByComparingTo("-2000000.00");
        assertThat(report.isProfitable()).isFalse();
        assertThat(report.getResultLabel()).isEqualTo("Loss Before Taxes");
        assertThat(report.getResultLabelEs()).isEqualTo("Pérdida antes de Impuestos");
        // Magnitude is always positive -- the sign is conveyed by the label.
        assertThat(report.getNetResultMagnitude()).isEqualByComparingTo("2000000.00");
        assertThat(report.getTotalCosts()).isEqualByComparingTo("4000000.00");
    }

    @Test
    @DisplayName("getIncomeStatementDetailed() reads period activity through getPeriodActivityExcludingClosingEntries(), never the plain getPeriodActivity() the CIERRE closing entry would double back into")
    void getIncomeStatementDetailed_usesClosingEntryExcludingQuery_regressionGuard() {
        // BUG FIX (2026-10-12): reported by the user -- the Estado de
        // Resultados came back empty for any closed fiscal year. Root
        // cause: it originally called the same getPeriodActivity() the
        // Trial Balance Detallado uses, which (correctly, for a trial
        // balance) includes the auto-generated "CIERRE" closing entry.
        // That entry is dated December 31 of the SAME year being
        // reported and zeroes out every Ingresos/Gastos/Costos account
        // by design (that's how the net result moves to Patrimonio), so
        // summing it together with the year's real activity netted
        // every result account to zero -- a closed year looked like it
        // had no P&L activity at all, while an open year (no closing
        // entry yet) looked fine. This test doesn't re-simulate a real
        // closing entry's lines (that's what
        // getPeriodActivityExcludingClosingEntries()'s own SQL filter
        // is for, exercised indirectly by every other test above via
        // its mock) -- it guards against a future refactor silently
        // reverting the service to call plain getPeriodActivity()
        // again, which would reintroduce this exact bug for every
        // closed year.
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 12, 31);

        when(chartOfAccountsRepository.findByCompanyIdAndActiveTrueOrderByCodeAsc(COMPANY_ID))
                .thenReturn(List.of());
        when(chartOfAccountsRepository.getPeriodActivityExcludingClosingEntries(eq(COMPANY_ID), eq(start), eq(end)))
                .thenReturn(List.of());

        service.getIncomeStatementDetailed(start, end);

        verify(chartOfAccountsRepository).getPeriodActivityExcludingClosingEntries(eq(COMPANY_ID), eq(start), eq(end));
        verify(chartOfAccountsRepository, never()).getPeriodActivity(any(), any(), any());
    }
}
