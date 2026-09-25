package com.erp.erp_cloud.service.reports.financial;

import com.erp.erp_cloud.dto.reports.financial.ThirdPartyBalanceGroup;
import com.erp.erp_cloud.dto.reports.financial.ThirdPartyBalanceReport;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.CostCenter;
import com.erp.erp_cloud.entity.JournalEntry;
import com.erp.erp_cloud.entity.JournalEntryItem;
import com.erp.erp_cloud.entity.ThirdParty;
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

class ThirdPartyReportServiceTest {

    private static final Long COMPANY_ID = 1L;
    private static final String START_CODE = "1";
    private static final String END_CODE = "9999999999";

    @Mock
    private JournalEntryRepository journalEntryRepository;

    private ThirdPartyReportService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ThirdPartyReportService(journalEntryRepository);

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

    private ThirdParty thirdParty(String document, String businessName) {
        ThirdParty tp = new ThirdParty();
        tp.setDocumentNumber(document);
        tp.setBusinessName(businessName);
        return tp;
    }

    private JournalEntryItem item(JournalEntry entry, ChartOfAccounts account, ThirdParty tp,
                                   BigDecimal debit, BigDecimal credit) {
        JournalEntryItem item = new JournalEntryItem();
        item.setJournalEntry(entry);
        item.setAccount(account);
        item.setThirdParty(tp);
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
    @DisplayName("getThirdPartyBalanceReport() carries the opening balance forward and computes the running balance as opening balance + debits - credits, IGNORING each line's account nature")
    void getThirdPartyBalanceReport_computesRunningBalance_debitIncreasesCreditDecreases_regardlessOfAccountNature() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);

        ThirdParty cliente = thirdParty("900123456", "GAMBITO DE REINA LTDA.");
        // Same third party, two accounts with OPPOSITE natures -- e.g. a
        // customer that also occasionally acts as a supplier. Unlike the
        // Cost Center report, the third party statement must NOT swing
        // its sign convention per line's account nature: it behaves like
        // a receivable/payable subledger, always "+debit -credit" no
        // matter which account a line posted to (confirmed with the user
        // 2026-09-25, after an initial nature-aware version -- copied
        // from the Cost Center report's pattern -- produced numbers the
        // user correctly flagged as wrong).
        ChartOfAccounts cartera = account("13050501", "Nacionales", AccountNature.D);
        ChartOfAccounts ventas = account("413554", "Venta de Maquinaria", AccountNature.C);

        // Opening balance: 1,000,000 net Debit already accumulated for this third party.
        when(journalEntryRepository.getThirdPartyOpeningBalances(
                eq(COMPANY_ID), eq(start), eq(START_CODE), eq(END_CODE), isNull()))
                .thenReturn(List.<Object[]>of(new Object[]{
                        1L, "900123456", "GAMBITO DE REINA LTDA.", null, null, null, null, null,
                        new BigDecimal("1000000.00")
                }));

        JournalEntryItem debitLine = item(entry(LocalDate.of(2026, 1, 10), "CE-001"),
                cartera, cliente, new BigDecimal("200000.00"), BigDecimal.ZERO);
        JournalEntryItem creditLine = item(entry(LocalDate.of(2026, 1, 15), "FEV-002"),
                ventas, cliente, BigDecimal.ZERO, new BigDecimal("50000.00"));

        when(journalEntryRepository.findItemsForThirdPartyAuxiliary(
                eq(COMPANY_ID), eq(start), eq(end), eq(START_CODE), eq(END_CODE), isNull()))
                .thenReturn(List.of(debitLine, creditLine));

        ThirdPartyBalanceReport result =
                service.getThirdPartyBalanceReport(start, end, START_CODE, END_CODE, null, null);

        assertThat(result.getThirdPartyGroups()).hasSize(1);
        ThirdPartyBalanceGroup group = result.getThirdPartyGroups().get(0);

        assertThat(group.getThirdPartyDocument()).isEqualTo("900123456");
        assertThat(group.getThirdPartyName()).isEqualTo("GAMBITO DE REINA LTDA.");
        assertThat(group.getOpeningBalance()).isEqualByComparingTo("1000000.00");

        // Line 1: a debit of 200,000 -> balance increases, regardless of
        // this account's (D) nature.
        assertThat(group.getTransactions().get(0).getNewBalance()).isEqualByComparingTo("1200000.00");
        // Line 2: a credit of 50,000 -> balance DECREASES, even though
        // this line's account (413554, Ventas) has nature C. A
        // nature-aware calculation would have wrongly added it instead
        // (1,250,000) -- this is exactly the bug the user caught.
        assertThat(group.getTransactions().get(1).getNewBalance()).isEqualByComparingTo("1150000.00");

        assertThat(group.getClosingBalance()).isEqualByComparingTo("1150000.00");
        assertThat(group.getTotalDebits()).isEqualByComparingTo("200000.00");
        assertThat(group.getTotalCredits()).isEqualByComparingTo("50000.00");

        // Each line carries its own account code, since the group itself spans two accounts.
        assertThat(group.getTransactions().get(0).getAccountCode()).isEqualTo("13050501");
        assertThat(group.getTransactions().get(1).getAccountCode()).isEqualTo("413554");
    }

    @Test
    @DisplayName("getThirdPartyBalanceReport() skips a third party with zero opening balance and no transactions, but keeps one with only an opening balance")
    void getThirdPartyBalanceReport_skipsTrulyEmptyThirdParties() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);

        when(journalEntryRepository.getThirdPartyOpeningBalances(any(), any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{
                        2L, "800999888", "PROVEEDOR ACTIVO S.A.S.", null, null, null, null, null,
                        new BigDecimal("500.00")
                }));
        when(journalEntryRepository.findItemsForThirdPartyAuxiliary(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        ThirdPartyBalanceReport result =
                service.getThirdPartyBalanceReport(start, end, START_CODE, END_CODE, null, null);

        assertThat(result.getThirdPartyGroups()).hasSize(1);
        assertThat(result.getThirdPartyGroups().get(0).getThirdPartyDocument()).isEqualTo("800999888");
        assertThat(result.getThirdPartyGroups().get(0).getClosingBalance()).isEqualByComparingTo("500.00");
        assertThat(result.getThirdPartyGroups().get(0).getTransactions()).isEmpty();
    }

    @Test
    @DisplayName("getThirdPartyBalanceReport() filters to a single third party when thirdPartyDocument is provided, and resolves its name from the result")
    void getThirdPartyBalanceReport_withThirdPartyFilter_resolvesNameAndPassesFilterThrough() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);

        ThirdParty cliente = thirdParty("900123456", "GAMBITO DE REINA LTDA.");
        ChartOfAccounts ventas = account("413554", "Venta de Maquinaria", AccountNature.C);

        when(journalEntryRepository.getThirdPartyOpeningBalances(
                eq(COMPANY_ID), eq(start), eq(START_CODE), eq(END_CODE), eq("900123456")))
                .thenReturn(List.of());
        when(journalEntryRepository.findItemsForThirdPartyAuxiliary(
                eq(COMPANY_ID), eq(start), eq(end), eq(START_CODE), eq(END_CODE), eq("900123456")))
                .thenReturn(List.of(item(entry(LocalDate.of(2026, 3, 1), "FEV-010"),
                        ventas, cliente, BigDecimal.ZERO, new BigDecimal("300000.00"))));

        ThirdPartyBalanceReport result =
                service.getThirdPartyBalanceReport(start, end, START_CODE, END_CODE, "900123456", null);

        assertThat(result.getThirdPartyDocument()).isEqualTo("900123456");
        assertThat(result.getThirdPartyName()).isEqualTo("GAMBITO DE REINA LTDA.");
        assertThat(result.getThirdPartyGroups()).hasSize(1);
    }

    @Test
    @DisplayName("getThirdPartyBalanceReport() calls the cost-center-scoped repository methods when a cost center filter is provided")
    void getThirdPartyBalanceReport_withCostCenterFilter_usesCostCenterScopedQueries() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);

        ThirdParty cliente = thirdParty("900123456", "GAMBITO DE REINA LTDA.");
        ChartOfAccounts ventas = account("413554", "Venta de Maquinaria", AccountNature.C);

        CostCenter ventasCC = new CostCenter();
        ventasCC.setCode("2001");
        ventasCC.setName("Ventas Nacionales");

        JournalEntryItem lineWithCc = item(entry(LocalDate.of(2026, 3, 1), "FEV-010"),
                ventas, cliente, BigDecimal.ZERO, new BigDecimal("300000.00"));
        lineWithCc.setCostCenter(ventasCC);

        when(journalEntryRepository.getThirdPartyOpeningBalancesForCostCenter(
                eq(COMPANY_ID), eq(start), eq(START_CODE), eq(END_CODE), isNull(), eq("2001")))
                .thenReturn(List.of());
        when(journalEntryRepository.findItemsForThirdPartyAuxiliaryByCostCenter(
                eq(COMPANY_ID), eq(start), eq(end), eq(START_CODE), eq(END_CODE), isNull(), eq("2001")))
                .thenReturn(List.of(lineWithCc));

        ThirdPartyBalanceReport result =
                service.getThirdPartyBalanceReport(start, end, START_CODE, END_CODE, null, "2001");

        assertThat(result.getCostCenterCode()).isEqualTo("2001");
        assertThat(result.getCostCenterName()).isEqualTo("Ventas Nacionales");
        assertThat(result.getThirdPartyGroups()).hasSize(1);
        assertThat(result.getThirdPartyGroups().get(0).getTransactions().get(0).getCostCenterCode())
                .isEqualTo("2001");
    }
}
