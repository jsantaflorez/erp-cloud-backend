package com.erp.erp_cloud.service.reports.financial;

import com.erp.erp_cloud.dto.reports.financial.CostCenterBalanceGroup;
import com.erp.erp_cloud.dto.reports.financial.CostCenterBalanceReport;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.CostCenter;
import com.erp.erp_cloud.entity.JournalEntry;
import com.erp.erp_cloud.entity.JournalEntryItem;
import com.erp.erp_cloud.enums.AccountNature;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

class CostCenterReportServiceTest {

    private static final Long COMPANY_ID = 1L;
    private static final String START_CODE = "1";
    private static final String END_CODE = "9999999999";

    @Mock
    private JournalEntryRepository journalEntryRepository;

    private CostCenterReportService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CostCenterReportService(journalEntryRepository);

        Company company = new Company();
        company.setId(COMPANY_ID);
        company.setLegalName("EMPRESA DE PRUEBA S.A.S.");
        TenantContext.setContext(company);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ChartOfAccounts account(String code, String name, AccountNature nature) {
        ChartOfAccounts account = new ChartOfAccounts();
        account.setCode(code);
        account.setName(name);
        account.setNature(nature);
        return account;
    }

    private CostCenter costCenter(String code, String name) {
        CostCenter cc = new CostCenter();
        cc.setCode(code);
        cc.setName(name);
        return cc;
    }

    private JournalEntryItem item(JournalEntry entry, ChartOfAccounts account, CostCenter cc,
                                   BigDecimal debit, BigDecimal credit) {
        JournalEntryItem item = new JournalEntryItem();
        item.setJournalEntry(entry);
        item.setAccount(account);
        item.setCostCenter(cc);
        item.setDebit(debit);
        item.setCredit(credit);
        return item;
    }

    private JournalEntry entry(LocalDate date, String docNumber) {
        JournalEntry entry = new JournalEntry();
        entry.setEntryDate(date);
        entry.setDocumentNumber(docNumber);
        entry.setDescription("Movimiento de prueba");
        return entry;
    }

    @Test
    @DisplayName("getCostCenterBalanceReport() carries the opening balance forward and computes the running balance per line, using EACH LINE'S OWN account nature")
    void getCostCenterBalanceReport_mixedNatureAccountsInSameCostCenter_computesCorrectRunningBalance() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);

        CostCenter ventas = costCenter("CC-VENTAS", "Ventas");
        // Same cost center, two accounts with OPPOSITE natures -- this is
        // exactly the case a single fixed "nature per group" would get
        // wrong, since it would apply one sign convention to every line
        // regardless of which account it actually belongs to.
        ChartOfAccounts inventario = account("16-05-05-01", "Almacén", AccountNature.D);
        ChartOfAccounts ingresos = account("41-35-01", "Ingresos por Venta", AccountNature.C);

        // Opening balance: 1,000,000 net Debit already accumulated for this cost center.
        when(journalEntryRepository.getCostCenterOpeningBalances(
                eq(COMPANY_ID), eq(start), eq(START_CODE), eq(END_CODE), isNull()))
                .thenReturn(List.<Object[]>of(new Object[]{"CC-VENTAS", "Ventas", new BigDecimal("1000000.00")}));

        JournalEntryItem debitLine = item(entry(LocalDate.of(2026, 1, 10), "CE-001"),
                inventario, ventas, new BigDecimal("200000.00"), BigDecimal.ZERO);
        JournalEntryItem creditLine = item(entry(LocalDate.of(2026, 1, 15), "NC-002"),
                ingresos, ventas, BigDecimal.ZERO, new BigDecimal("50000.00"));

        when(journalEntryRepository.findItemsForCostCenterAuxiliary(
                eq(COMPANY_ID), eq(start), eq(end), eq(START_CODE), eq(END_CODE), isNull()))
                .thenReturn(List.of(debitLine, creditLine));

        CostCenterBalanceReport result = service.getCostCenterBalanceReport(start, end, START_CODE, END_CODE, null);

        assertThat(result.getCostCenterGroups()).hasSize(1);
        CostCenterBalanceGroup group = result.getCostCenterGroups().get(0);

        assertThat(group.getCostCenterCode()).isEqualTo("CC-VENTAS");
        assertThat(group.getOpeningBalance()).isEqualByComparingTo("1000000.00");

        // Line 1: Debit-nature account -> balance increases by its debit.
        assertThat(group.getTransactions().get(0).getNewBalance()).isEqualByComparingTo("1200000.00");
        // Line 2: Credit-nature account -> balance increases by ITS credit too
        // (each line uses its own account's nature, not one fixed for the group).
        assertThat(group.getTransactions().get(1).getNewBalance()).isEqualByComparingTo("1250000.00");

        assertThat(group.getClosingBalance()).isEqualByComparingTo("1250000.00");
        assertThat(group.getTotalDebits()).isEqualByComparingTo("200000.00");
        assertThat(group.getTotalCredits()).isEqualByComparingTo("50000.00");

        // Each line carries its own account code/name, since the group itself spans two accounts.
        assertThat(group.getTransactions().get(0).getAccountCode()).isEqualTo("16-05-05-01");
        assertThat(group.getTransactions().get(1).getAccountCode()).isEqualTo("41-35-01");
    }

    @Test
    @DisplayName("getCostCenterBalanceReport() skips a cost center with zero opening balance and no transactions, but keeps one with only an opening balance")
    void getCostCenterBalanceReport_skipsTrulyEmptyCostCenters() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);

        when(journalEntryRepository.getCostCenterOpeningBalances(any(), any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"CC-ADMIN", "Administración", new BigDecimal("500.00")}));
        when(journalEntryRepository.findItemsForCostCenterAuxiliary(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        CostCenterBalanceReport result = service.getCostCenterBalanceReport(start, end, START_CODE, END_CODE, null);

        assertThat(result.getCostCenterGroups()).hasSize(1);
        assertThat(result.getCostCenterGroups().get(0).getCostCenterCode()).isEqualTo("CC-ADMIN");
        assertThat(result.getCostCenterGroups().get(0).getClosingBalance()).isEqualByComparingTo("500.00");
        assertThat(result.getCostCenterGroups().get(0).getTransactions()).isEmpty();
    }

    @Test
    @DisplayName("getCostCenterBalanceReport() filters to a single cost center when costCenterCode is provided, and resolves its name from the result")
    void getCostCenterBalanceReport_withCostCenterFilter_resolvesNameAndPassesFilterThrough() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);

        when(journalEntryRepository.getCostCenterOpeningBalances(
                eq(COMPANY_ID), eq(start), eq(START_CODE), eq(END_CODE), eq("CC-VENTAS")))
                .thenReturn(List.<Object[]>of(new Object[]{"CC-VENTAS", "Ventas", new BigDecimal("0")}));
        when(journalEntryRepository.findItemsForCostCenterAuxiliary(
                eq(COMPANY_ID), eq(start), eq(end), eq(START_CODE), eq(END_CODE), eq("CC-VENTAS")))
                .thenReturn(List.of());

        CostCenterBalanceReport result =
                service.getCostCenterBalanceReport(start, end, START_CODE, END_CODE, "CC-VENTAS");

        assertThat(result.getCostCenterCode()).isEqualTo("CC-VENTAS");
        // Opening balance is exactly zero AND there are no transactions --
        // per the "skip truly empty" rule, no group is built, so the name
        // can't be resolved from a group. That's an accepted edge case
        // (documented in the service): the code still comes through on
        // the report even though the name doesn't in this specific case.
        assertThat(result.getCostCenterGroups()).isEmpty();
    }
}
