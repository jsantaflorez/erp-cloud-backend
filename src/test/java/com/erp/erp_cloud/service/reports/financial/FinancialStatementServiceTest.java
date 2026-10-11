package com.erp.erp_cloud.service.reports.financial;

import com.erp.erp_cloud.dto.reports.financial.BalanceSheetReport;
import com.erp.erp_cloud.dto.reports.financial.BalanceSheetSection;
import com.erp.erp_cloud.dto.reports.financial.IncomeStatementReport;
import com.erp.erp_cloud.dto.reports.financial.IncomeStatementSection;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.enums.AccountCategory;
import com.erp.erp_cloud.enums.AccountClass;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    private FinancialStatementService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new FinancialStatementService(journalEntryRepository, chartOfAccountsRepository);

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
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Pasivos nets a contra-liability (IVA Descontable, nature D) against a normal liability (IVA Generado, nature C) instead of adding them")
    void getBalanceSheet_netsContraLiabilityAgainstNormalLiabilityWithinSameCategory() {
        // IVA Generado del 19%: nature C, credit balance of 2,280,000 -- a real liability.
        // IVA Descontable por Compras del 19%: nature D, debit balance of 957,983 --
        // a contra-liability that should REDUCE the net amount owed, exactly like it
        // does in the Balance de Comprobación Detallado and the legacy SIEWIN report.
        when(journalEntryRepository.getAccountBalancesAsOfDate(eq(COMPANY_ID), eq(AS_OF_DATE)))
                .thenReturn(List.of(
                        new Object[]{"240805", "C", BigDecimal.ZERO, new BigDecimal("2280000.00")},
                        new Object[]{"240810", "D", new BigDecimal("957983.00"), BigDecimal.ZERO}
                ));

        when(chartOfAccountsRepository.getAccountsForBalanceSheet(eq(COMPANY_ID), eq(AccountClass.LIABILITY)))
                .thenReturn(List.of(
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
                .thenReturn(List.of(
                        new Object[]{"220505", "C", BigDecimal.ZERO, new BigDecimal("1000000.00")},
                        new Object[]{"220510", "C", BigDecimal.ZERO, new BigDecimal("500000.00")}
                ));

        when(chartOfAccountsRepository.getAccountsForBalanceSheet(eq(COMPANY_ID), eq(AccountClass.LIABILITY)))
                .thenReturn(List.of(
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
                .thenReturn(List.of(
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
}
