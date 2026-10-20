package com.erp.erp_cloud.service.reports.financial;

import com.erp.erp_cloud.dto.reports.financial.IncompleteMovementsReport;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.CostCenter;
import com.erp.erp_cloud.entity.JournalEntry;
import com.erp.erp_cloud.entity.JournalEntryItem;
import com.erp.erp_cloud.entity.ThirdParty;
import com.erp.erp_cloud.exception.InvalidOperationException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers IncompleteMovementsReportService (2026-10-20, user request --
 * see IncompleteMovementLine's javadoc for the full rationale: finding
 * lines left "orphaned" after an account's requiresThirdParty/
 * requiresCostCenter flag was turned on after the line was already
 * posted). The actual row-level filtering (which lines come back) lives
 * in JournalEntryRepository.findIncompleteMovementItems()'s JPQL, which
 * is mocked here same as every other report's repository query in this
 * package -- these tests cover the service's own mapping (which flag(s)
 * get flipped, which side the amount comes from, journalEntryId
 * round-tripping for the frontend's Editar button) and its date-range
 * validation.
 */
class IncompleteMovementsReportServiceTest {

    private static final Long COMPANY_ID = 1L;

    @Mock
    private JournalEntryRepository journalEntryRepository;

    private IncompleteMovementsReportService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new IncompleteMovementsReportService(journalEntryRepository);

        Company company = new Company();
        company.setId(COMPANY_ID);
        company.setLegalName("EMPRESA DE PRUEBA S.A.S.");
        TenantContext.setContext(company);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ChartOfAccounts account(String code, String name, boolean requiresTp, boolean requiresCc) {
        ChartOfAccounts acc = new ChartOfAccounts();
        acc.setCode(code);
        acc.setName(name);
        acc.setRequiresThirdParty(requiresTp);
        acc.setRequiresCostCenter(requiresCc);
        return acc;
    }

    private JournalEntry entry(Long id, LocalDate date, String docNumber) {
        JournalEntry entry = new JournalEntry();
        entry.setId(id);
        entry.setEntryDate(date);
        entry.setDocumentNumber(docNumber);
        return entry;
    }

    private JournalEntryItem item(JournalEntry je, ChartOfAccounts acc, ThirdParty tp, CostCenter cc,
                                   BigDecimal debit, BigDecimal credit) {
        JournalEntryItem item = new JournalEntryItem();
        item.setJournalEntry(je);
        item.setAccount(acc);
        item.setThirdParty(tp);
        item.setCostCenter(cc);
        item.setDebit(debit);
        item.setCredit(credit);
        return item;
    }

    @Test
    @DisplayName("getIncompleteMovementsReport() flags a line whose account requires a Third Party it doesn't have")
    void flagsLineMissingRequiredThirdParty() {
        ChartOfAccounts acc = account("130505", "Clientes Nacionales", true, false);
        JournalEntry je = entry(42L, LocalDate.of(2026, 10, 1), "FV-10");
        JournalEntryItem orphanItem = item(je, acc, null, null, new BigDecimal("500000"), BigDecimal.ZERO);

        when(journalEntryRepository.findIncompleteMovementItems(COMPANY_ID, null, null))
                .thenReturn(List.of(orphanItem));

        IncompleteMovementsReport report = service.getIncompleteMovementsReport(null, null);

        assertThat(report.getTotalRecords()).isEqualTo(1);
        var line = report.getLines().get(0);
        assertThat(line.getJournalEntryId()).isEqualTo(42L);
        assertThat(line.isMissingThirdParty()).isTrue();
        assertThat(line.isMissingCostCenter()).isFalse();
        assertThat(line.getAmount()).isEqualByComparingTo("500000");
        assertThat(line.getSide()).isEqualTo("D");
    }

    @Test
    @DisplayName("getIncompleteMovementsReport() flags a line whose account requires a Cost Center it doesn't have, and reads the credit side")
    void flagsLineMissingRequiredCostCenter_readsCreditSide() {
        ChartOfAccounts acc = account("519530", "Gastos de Mantenimiento", false, true);
        JournalEntry je = entry(43L, LocalDate.of(2026, 10, 2), "CE-7");
        JournalEntryItem orphanItem = item(je, acc, null, null, BigDecimal.ZERO, new BigDecimal("120000"));

        when(journalEntryRepository.findIncompleteMovementItems(COMPANY_ID, null, null))
                .thenReturn(List.of(orphanItem));

        IncompleteMovementsReport report = service.getIncompleteMovementsReport(null, null);

        var line = report.getLines().get(0);
        assertThat(line.isMissingCostCenter()).isTrue();
        assertThat(line.isMissingThirdParty()).isFalse();
        assertThat(line.getAmount()).isEqualByComparingTo("120000");
        assertThat(line.getSide()).isEqualTo("C");
    }

    @Test
    @DisplayName("getIncompleteMovementsReport() passes an optional date range straight through to the repository")
    void passesOptionalDateRangeThrough() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        when(journalEntryRepository.findIncompleteMovementItems(COMPANY_ID, start, end))
                .thenReturn(List.of());

        IncompleteMovementsReport report = service.getIncompleteMovementsReport(start, end);

        verify(journalEntryRepository).findIncompleteMovementItems(eq(COMPANY_ID), eq(start), eq(end));
        assertThat(report.getStartDate()).isEqualTo(start);
        assertThat(report.getEndDate()).isEqualTo(end);
        assertThat(report.getTotalRecords()).isZero();
    }

    @Test
    @DisplayName("getIncompleteMovementsReport() works with no date range at all (whole history)")
    void worksWithNoDateRangeAtAll() {
        when(journalEntryRepository.findIncompleteMovementItems(eq(COMPANY_ID), isNull(), isNull()))
                .thenReturn(List.of());

        IncompleteMovementsReport report = service.getIncompleteMovementsReport(null, null);

        verify(journalEntryRepository).findIncompleteMovementItems(eq(COMPANY_ID), isNull(), isNull());
        assertThat(report.getStartDate()).isNull();
        assertThat(report.getEndDate()).isNull();
    }

    @Test
    @DisplayName("getIncompleteMovementsReport() rejects an inverted date range")
    void rejectsInvertedDateRange() {
        assertThatThrownBy(() -> service.getIncompleteMovementsReport(
                LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(InvalidOperationException.class);
    }
}
