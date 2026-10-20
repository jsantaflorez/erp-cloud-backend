package com.erp.erp_cloud.service.reports.financial;

import com.erp.erp_cloud.dto.reports.financial.IncompleteMovementLine;
import com.erp.erp_cloud.dto.reports.financial.IncompleteMovementsReport;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.JournalEntryItem;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.security.context.TenantContext;
import com.erp.erp_cloud.service.base.TenantAwareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Builds the "Informe de Movimientos Incompletos" (2026-10-20, user
 * request): audits every journal entry line against its account's
 * CURRENT requiresThirdParty/requiresCostCenter configuration, surfacing
 * lines that don't have what the account now demands -- almost always
 * because the account was loosened/tightened after the line was
 * already posted. See IncompleteMovementLine's javadoc for the full
 * rationale.
 *
 * Unlike every other report in this package, the date range here is
 * OPTIONAL and unbounded by default: this is a cleanup/audit tool meant
 * to find every historical orphan across the company's whole history,
 * not a period report -- narrowing by date is a convenience for a
 * company that already knows roughly when the account was changed, not
 * a requirement.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IncompleteMovementsReportService extends TenantAwareService {

    private final JournalEntryRepository journalEntryRepository;

    public IncompleteMovementsReport getIncompleteMovementsReport(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new InvalidOperationException(
                    "End date cannot be before start date.", "INVALID_DATE_RANGE");
        }

        Long companyId = currentTenantId();
        Company company = TenantContext.getCurrentCompany();

        List<JournalEntryItem> items =
                journalEntryRepository.findIncompleteMovementItems(companyId, startDate, endDate);

        List<IncompleteMovementLine> lines = items.stream().map(this::toLine).toList();

        return IncompleteMovementsReport.builder()
                .companyName(company.getLegalName())
                .reportTitle("INFORME DE MOVIMIENTOS INCOMPLETOS")
                .startDate(startDate)
                .endDate(endDate)
                .generatedAt(LocalDateTime.now())
                .lines(lines)
                .totalRecords(lines.size())
                .build();
    }

    private IncompleteMovementLine toLine(JournalEntryItem item) {
        BigDecimal debit = item.getDebit() != null ? item.getDebit() : BigDecimal.ZERO;
        BigDecimal credit = item.getCredit() != null ? item.getCredit() : BigDecimal.ZERO;
        boolean isDebit = debit.compareTo(BigDecimal.ZERO) != 0;

        return IncompleteMovementLine.builder()
                .journalEntryId(item.getJournalEntry().getId())
                .entryDate(item.getJournalEntry().getEntryDate())
                .documentNumber(item.getJournalEntry().getDocumentNumber())
                .accountCode(item.getAccount().getCode())
                .accountName(item.getAccount().getName())
                .missingThirdParty(item.getAccount().isRequiresThirdParty() && item.getThirdParty() == null)
                .missingCostCenter(item.getAccount().isRequiresCostCenter() && item.getCostCenter() == null)
                .amount(isDebit ? debit : credit)
                .side(isDebit ? "D" : "C")
                .build();
    }
}
