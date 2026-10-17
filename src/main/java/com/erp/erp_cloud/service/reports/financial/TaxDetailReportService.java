package com.erp.erp_cloud.service.reports.financial;

import com.erp.erp_cloud.dto.reports.financial.TaxDetailLine;
import com.erp.erp_cloud.dto.reports.financial.TaxDetailReport;
import com.erp.erp_cloud.dto.reports.financial.TaxTypeSubtotal;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.JournalEntryItem;
import com.erp.erp_cloud.entity.ThirdParty;
import com.erp.erp_cloud.enums.TaxCategory;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.repository.ThirdPartyRepository;
import com.erp.erp_cloud.security.context.TenantContext;
import com.erp.erp_cloud.service.base.TenantAwareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the "Informe Detallado de Impuestos y Retenciones" (2026-10-16):
 * a single consolidated report over every tax-linked journal entry line,
 * covering IVA, Retenciones, Impoconsumo and any future custom tax --
 * explicitly requested by the user instead of one report per Tax
 * category. Category (RETENCION/IVA/OTRO) is an optional filter here,
 * not a hardcoded constant, so the same code path serves "solo IVA del
 * mes", "solo retenciones" or "todo junto" depending on what's picked
 * on screen. The report is what the accountant uses to liquidate
 * monthly/period tax declarations and validate bases before issuing
 * certificates, for whichever tax category is being declared.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaxDetailReportService extends TenantAwareService {

    private final JournalEntryRepository journalEntryRepository;
    private final ThirdPartyRepository thirdPartyRepository;

    public TaxDetailReport getTaxDetailReport(
            LocalDate startDate,
            LocalDate endDate,
            TaxCategory category,
            String taxType,
            String thirdPartyDocument) {

        if (startDate == null || endDate == null) {
            throw new InvalidOperationException(
                    "Start date and end date are required.", "DATE_RANGE_REQUIRED");
        }
        if (endDate.isBefore(startDate)) {
            throw new InvalidOperationException(
                    "End date cannot be before start date.", "INVALID_DATE_RANGE");
        }

        Long companyId = currentTenantId();
        Company company = TenantContext.getCurrentCompany();

        // Same normalization TaxPage.jsx applies when saving `type` --
        // a filter typed in a different case must still match.
        String typeFilter = (taxType != null && !taxType.isBlank())
                ? taxType.trim().toUpperCase()
                : null;
        String thirdPartyFilter = (thirdPartyDocument != null && !thirdPartyDocument.isBlank())
                ? thirdPartyDocument.trim()
                : null;

        // Resolved only for the report header/export -- the query itself
        // filters by document number, not by the ThirdParty entity.
        String thirdPartyNameFilter = null;
        if (thirdPartyFilter != null) {
            thirdPartyNameFilter = thirdPartyRepository
                    .findByCompanyIdAndDocumentNumber(companyId, thirdPartyFilter)
                    .map(ThirdParty::getLegalDisplayName)
                    .orElse(null);
        }

        List<JournalEntryItem> items = journalEntryRepository.findTaxItems(
                companyId, category, startDate, endDate, typeFilter, thirdPartyFilter);

        List<TaxDetailLine> lines = items.stream().map(this::toLine).toList();

        // Subtotals grouped by (category, type) pair -- e.g. "RETENCION /
        // RETEFUENTE" stays distinct from "IVA / IVA_GENERADO" even when
        // no category filter narrows the report to just one of them.
        Map<String, TaxTypeSubtotal> subtotalsByKey = new LinkedHashMap<>();
        for (TaxDetailLine line : lines) {
            String key = line.getTaxCategory() + "|" + line.getTaxType();
            TaxTypeSubtotal running = subtotalsByKey.get(key);
            if (running == null) {
                subtotalsByKey.put(key, TaxTypeSubtotal.builder()
                        .taxCategory(line.getTaxCategory())
                        .taxType(line.getTaxType())
                        .recordCount(1)
                        .totalBase(nullToZero(line.getTaxBase()))
                        .totalValue(nullToZero(line.getTaxValue()))
                        .build());
            } else {
                subtotalsByKey.put(key, TaxTypeSubtotal.builder()
                        .taxCategory(running.getTaxCategory())
                        .taxType(running.getTaxType())
                        .recordCount(running.getRecordCount() + 1)
                        .totalBase(running.getTotalBase().add(nullToZero(line.getTaxBase())))
                        .totalValue(running.getTotalValue().add(nullToZero(line.getTaxValue())))
                        .build());
            }
        }

        List<TaxTypeSubtotal> subtotalsByType = new ArrayList<>(subtotalsByKey.values());
        subtotalsByType.sort(
                Comparator.comparing(TaxTypeSubtotal::getTaxCategory)
                        .thenComparing(TaxTypeSubtotal::getTaxType));

        BigDecimal totalBase = lines.stream()
                .map(l -> nullToZero(l.getTaxBase()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalValue = lines.stream()
                .map(l -> nullToZero(l.getTaxValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return TaxDetailReport.builder()
                .companyName(company.getLegalName())
                .reportTitle("INFORME DETALLADO DE IMPUESTOS Y RETENCIONES")
                .startDate(startDate)
                .endDate(endDate)
                .generatedAt(LocalDateTime.now())
                .categoryFilter(category != null ? category.name() : null)
                .taxTypeFilter(typeFilter)
                .thirdPartyDocumentFilter(thirdPartyFilter)
                .thirdPartyNameFilter(thirdPartyNameFilter)
                .lines(lines)
                .subtotalsByType(subtotalsByType)
                .totalBase(totalBase)
                .totalValue(totalValue)
                .totalRecords(lines.size())
                .build();
    }

    private TaxDetailLine toLine(JournalEntryItem item) {
        BigDecimal debit = item.getDebit() != null ? item.getDebit() : BigDecimal.ZERO;
        BigDecimal credit = item.getCredit() != null ? item.getCredit() : BigDecimal.ZERO;
        // Same rule the old Retention report used: the computed tax/
        // withholding value is whichever side handleTax() actually
        // populated (debit for a D-sign tax, credit for a C-sign one) --
        // there's no universal "contra" convention across categories
        // (IVA Generado is credit-normal, IVA Descontable is debit-
        // normal, a retention is whatever its own Tax.sign says), so
        // checking "debit first, else credit" covers every case without
        // needing to branch on category here.
        BigDecimal taxValue = debit.compareTo(BigDecimal.ZERO) != 0 ? debit : credit;

        return TaxDetailLine.builder()
                .entryDate(item.getJournalEntry().getEntryDate())
                .documentNumber(item.getJournalEntry().getDocumentNumber())
                .thirdPartyDocument(item.getThirdParty() != null
                        ? item.getThirdParty().getDocumentNumber() : null)
                .thirdPartyName(item.getThirdParty() != null
                        ? item.getThirdParty().getLegalDisplayName() : null)
                .detail(item.getDescription() != null
                        ? item.getDescription()
                        : item.getJournalEntry().getDescription())
                .accountCode(item.getAccount().getCode())
                .accountName(item.getAccount().getName())
                .taxCategory(item.getTax().getCategory().name())
                .taxType(item.getTax().getType())
                .taxRate(item.getTax().getRate())
                .taxBase(item.getTaxBase())
                .taxValue(taxValue)
                .build();
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
