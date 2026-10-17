package com.erp.erp_cloud.service.reports.financial;

import com.erp.erp_cloud.dto.reports.financial.TaxDetailReport;
import com.erp.erp_cloud.dto.reports.financial.TaxDetailLine;
import com.erp.erp_cloud.dto.reports.financial.TaxTypeSubtotal;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.JournalEntry;
import com.erp.erp_cloud.entity.JournalEntryItem;
import com.erp.erp_cloud.entity.Tax;
import com.erp.erp_cloud.entity.ThirdParty;
import com.erp.erp_cloud.enums.TaxCategory;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.repository.ThirdPartyRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers TaxDetailReportService, which replaced the single-category
 * RetentionReportService (2026-10-16, user asked to unify every Tax
 * category into one report instead of one per category). Tests
 * exercise exactly the behavior that changed with the unification:
 * category now optional, subtotals grouped per (category, type) pair
 * instead of per type alone, and the same debit/credit/validation
 * rules the Retention-only version already had proven.
 */
class TaxDetailReportServiceTest {

    private static final Long COMPANY_ID = 1L;

    @Mock
    private JournalEntryRepository journalEntryRepository;

    @Mock
    private ThirdPartyRepository thirdPartyRepository;

    private TaxDetailReportService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new TaxDetailReportService(journalEntryRepository, thirdPartyRepository);

        Company company = new Company();
        company.setId(COMPANY_ID);
        company.setLegalName("EMPRESA DE PRUEBA S.A.S.");
        TenantContext.setContext(company);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ChartOfAccounts account(String code, String name) {
        ChartOfAccounts acc = new ChartOfAccounts();
        acc.setCode(code);
        acc.setName(name);
        return acc;
    }

    private Tax tax(String code, String type, TaxCategory category, String rate, String sign, ChartOfAccounts acc) {
        Tax tax = new Tax();
        tax.setId(900L);
        tax.setCode(code);
        tax.setName(code);
        tax.setType(type);
        tax.setCategory(category);
        tax.setRate(new BigDecimal(rate));
        tax.setSign(sign);
        tax.setActive(true);
        tax.setAccount(acc);
        return tax;
    }

    private ThirdParty thirdParty(String document, String businessName) {
        ThirdParty tp = new ThirdParty();
        tp.setDocumentNumber(document);
        tp.setBusinessName(businessName);
        return tp;
    }

    private JournalEntry entry(LocalDate date, String docNumber) {
        JournalEntry entry = new JournalEntry();
        entry.setEntryDate(date);
        entry.setDocumentNumber(docNumber);
        entry.setDescription("Comprobante de prueba");
        return entry;
    }

    private JournalEntryItem item(JournalEntry je, ChartOfAccounts acc, Tax tax, ThirdParty tp,
                                   BigDecimal base, BigDecimal debit, BigDecimal credit) {
        JournalEntryItem item = new JournalEntryItem();
        item.setJournalEntry(je);
        item.setAccount(acc);
        item.setTax(tax);
        item.setTaxBase(base);
        item.setThirdParty(tp);
        item.setDebit(debit);
        item.setCredit(credit);
        return item;
    }

    @Test
    @DisplayName("getTaxDetailReport() maps lines and groups subtotals per (category, type) pair across categories")
    void getTaxDetailReport_buildsLinesAndSubtotalsAcrossCategories() {
        ChartOfAccounts reteAccount = account("236540", "Retefuente por Pagar");
        ChartOfAccounts ivaAccount = account("240801", "IVA Generado");
        ThirdParty tp = thirdParty("900123456", "PROVEEDOR SAS");

        Tax reteTax = tax("RF1", "RETEFUENTE", TaxCategory.RETENCION, "3.5", "C", reteAccount);
        Tax ivaTax = tax("IVA19", "IVA_GENERADO", TaxCategory.IVA, "19", "C", ivaAccount);

        JournalEntry e1 = entry(LocalDate.of(2026, 10, 5), "FE-1");
        JournalEntry e2 = entry(LocalDate.of(2026, 10, 6), "FE-2");

        JournalEntryItem reteItem = item(e1, reteAccount, reteTax, tp,
                new BigDecimal("1000000"), BigDecimal.ZERO, new BigDecimal("35000"));
        JournalEntryItem ivaItem = item(e2, ivaAccount, ivaTax, tp,
                new BigDecimal("1000000"), BigDecimal.ZERO, new BigDecimal("190000"));

        when(journalEntryRepository.findTaxItems(
                eq(COMPANY_ID), isNull(), any(), any(), isNull(), isNull()))
                .thenReturn(List.of(reteItem, ivaItem));

        TaxDetailReport report = service.getTaxDetailReport(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null, null, null);

        assertThat(report.getLines()).hasSize(2);
        assertThat(report.getTotalRecords()).isEqualTo(2);
        assertThat(report.getTotalValue()).isEqualByComparingTo("225000");

        assertThat(report.getSubtotalsByType()).hasSize(2);
        TaxTypeSubtotal reteSubtotal = report.getSubtotalsByType().stream()
                .filter(s -> "RETENCION".equals(s.getTaxCategory())).findFirst().orElseThrow();
        assertThat(reteSubtotal.getTaxType()).isEqualTo("RETEFUENTE");
        assertThat(reteSubtotal.getTotalValue()).isEqualByComparingTo("35000");

        TaxTypeSubtotal ivaSubtotal = report.getSubtotalsByType().stream()
                .filter(s -> "IVA".equals(s.getTaxCategory())).findFirst().orElseThrow();
        assertThat(ivaSubtotal.getTaxType()).isEqualTo("IVA_GENERADO");
        assertThat(ivaSubtotal.getTotalValue()).isEqualByComparingTo("190000");
    }

    @Test
    @DisplayName("getTaxDetailReport() reads the tax value from whichever side (debit or credit) is non-zero")
    void getTaxDetailReport_readsTaxValueFromDebitSideToo() {
        ChartOfAccounts acc = account("135515", "Retefuente por Cobrar");
        Tax debitTax = tax("RF2", "RETEFUENTE", TaxCategory.RETENCION, "3.5", "D", acc);
        JournalEntry e = entry(LocalDate.of(2026, 10, 5), "FE-3");
        JournalEntryItem debitItem = item(e, acc, debitTax, thirdParty("900999888", "CLIENTE SAS"),
                new BigDecimal("500000"), new BigDecimal("17500"), BigDecimal.ZERO);

        when(journalEntryRepository.findTaxItems(
                eq(COMPANY_ID), eq(TaxCategory.RETENCION), any(), any(), isNull(), isNull()))
                .thenReturn(List.of(debitItem));

        TaxDetailReport report = service.getTaxDetailReport(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), TaxCategory.RETENCION, null, null);

        assertThat(report.getLines()).hasSize(1);
        assertThat(report.getLines().get(0).getTaxValue()).isEqualByComparingTo("17500");
    }

    @Test
    @DisplayName("getTaxDetailReport() normalizes the type filter to uppercase and resolves the third party's name")
    void getTaxDetailReport_normalizesTypeFilter_resolvesThirdPartyName() {
        when(thirdPartyRepository.findByCompanyIdAndDocumentNumber(COMPANY_ID, "900123456"))
                .thenReturn(Optional.of(thirdParty("900123456", "PROVEEDOR SAS")));
        when(journalEntryRepository.findTaxItems(
                eq(COMPANY_ID), eq(TaxCategory.RETENCION), any(), any(), eq("RETEFUENTE"), eq("900123456")))
                .thenReturn(List.of());

        TaxDetailReport report = service.getTaxDetailReport(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                TaxCategory.RETENCION, "retefuente", "900123456");

        verify(journalEntryRepository).findTaxItems(
                eq(COMPANY_ID), eq(TaxCategory.RETENCION), any(), any(), eq("RETEFUENTE"), eq("900123456"));
        assertThat(report.getThirdPartyNameFilter()).isEqualTo("PROVEEDOR SAS");
        assertThat(report.getTaxTypeFilter()).isEqualTo("RETEFUENTE");
    }

    @Test
    @DisplayName("getTaxDetailReport() validates the date range is present and not inverted")
    void getTaxDetailReport_validatesDateRange() {
        assertThatThrownBy(() -> service.getTaxDetailReport(null, LocalDate.now(), null, null, null))
                .isInstanceOf(InvalidOperationException.class);

        assertThatThrownBy(() -> service.getTaxDetailReport(
                LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 1), null, null, null))
                .isInstanceOf(InvalidOperationException.class);
    }
}
