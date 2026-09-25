package com.erp.erp_cloud.service.reports.financial;

import com.erp.erp_cloud.dto.reports.financial.AuxiliaryLedgerTransaction;
import com.erp.erp_cloud.dto.reports.financial.ThirdPartyBalanceGroup;
import com.erp.erp_cloud.dto.reports.financial.ThirdPartyBalanceReport;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.JournalEntryItem;
import com.erp.erp_cloud.entity.ThirdParty;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.security.context.TenantContext;
import com.erp.erp_cloud.service.base.TenantAwareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ThirdPartyReportService extends TenantAwareService {

    private final JournalEntryRepository journalEntryRepository;

    /**
     * Generates the "Estado de Cuenta por Tercero" report: opening
     * balance, transactions and closing balance per third party within a
     * date range. Same live-aggregation approach as the Cost Center and
     * Auxiliary Ledger reports (no stored balances), EXCEPT it is not
     * nature-aware -- see processRunningBalance()'s Javadoc for why a
     * third party's balance is always "opening balance + debits -
     * credits", regardless of which account each line posted to.
     *
     * @param startDate      start of the reporting period
     * @param endDate        end of the reporting period
     * @param startCode      first account code in range (default "1")
     * @param endCode        last account code in range (default "9999999999")
     * @param thirdPartyDocument optional: restrict to one third party by document number; null/blank = all
     * @param costCenterCode optional: restrict to one cost center; null/blank = all
     */
    public ThirdPartyBalanceReport getThirdPartyBalanceReport(
            LocalDate startDate,
            LocalDate endDate,
            String startCode,
            String endCode,
            String thirdPartyDocument,
            String costCenterCode) {

        Long companyId = currentTenantId();
        Company company = TenantContext.getCurrentCompany();

        String tpFilter = (thirdPartyDocument == null || thirdPartyDocument.isBlank()) ? null : thirdPartyDocument;
        String ccFilter = (costCenterCode == null || costCenterCode.isBlank()) ? null : costCenterCode;

        // 1. Opening balances per third party (opening balance + debits
        // - credits, not nature-aware -- see repository Javadoc). Two query
        // variants: with or without the optional cost center filter,
        // instead of one query with a null-guarded LEFT JOIN condition.
        List<Object[]> openingBalanceRows = ccFilter == null
                ? journalEntryRepository.getThirdPartyOpeningBalances(companyId, startDate, startCode, endCode, tpFilter)
                : journalEntryRepository.getThirdPartyOpeningBalancesForCostCenter(companyId, startDate, startCode, endCode, tpFilter, ccFilter);

        Map<String, BigDecimal> openingBalancesMap = openingBalanceRows.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[1],
                        row -> (BigDecimal) row[8]
                ));

        // ThirdParty has no single "name" column (unlike CostCenter), so
        // the query returns the raw name components and this rebuilds
        // the exact same display name ThirdParty.getLegalDisplayName()
        // would, via a transient (never persisted) instance -- reusing
        // that logic instead of duplicating it here.
        Map<String, String> namesFromOpeningBalances = openingBalanceRows.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[1],
                        this::buildDisplayNameFromRow,
                        (a, b) -> a
                ));

        // 2. Transaction items for the period.
        List<JournalEntryItem> allItems = ccFilter == null
                ? journalEntryRepository.findItemsForThirdPartyAuxiliary(companyId, startDate, endDate, startCode, endCode, tpFilter)
                : journalEntryRepository.findItemsForThirdPartyAuxiliaryByCostCenter(companyId, startDate, endDate, startCode, endCode, tpFilter, ccFilter);

        Map<String, List<JournalEntryItem>> itemsByThirdParty = allItems.stream()
                .collect(Collectors.groupingBy(item -> item.getThirdParty().getDocumentNumber()));

        // 3. Union of third party documents from either source.
        Set<String> allThirdPartyDocuments = new HashSet<>();
        allThirdPartyDocuments.addAll(openingBalancesMap.keySet());
        allThirdPartyDocuments.addAll(itemsByThirdParty.keySet());

        // 4. Build one group per third party.
        List<ThirdPartyBalanceGroup> groups = new ArrayList<>();

        for (String document : allThirdPartyDocuments.stream().sorted().toList()) {
            BigDecimal startBalance = openingBalancesMap.getOrDefault(document, BigDecimal.ZERO);
            List<JournalEntryItem> items = itemsByThirdParty.getOrDefault(document, new ArrayList<>());

            if (startBalance.compareTo(BigDecimal.ZERO) == 0 && items.isEmpty()) {
                continue;
            }

            String name = !items.isEmpty()
                    ? items.get(0).getThirdParty().getLegalDisplayName()
                    : namesFromOpeningBalances.get(document);

            List<AuxiliaryLedgerTransaction> transactionDTOs = processRunningBalance(items, startBalance);

            BigDecimal totalDebits = transactionDTOs.stream()
                    .map(t -> t.getDebit() != null ? t.getDebit() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalCredits = transactionDTOs.stream()
                    .map(t -> t.getCredit() != null ? t.getCredit() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal finalBalance = transactionDTOs.isEmpty()
                    ? startBalance
                    : transactionDTOs.get(transactionDTOs.size() - 1).getNewBalance();

            groups.add(ThirdPartyBalanceGroup.builder()
                    .thirdPartyDocument(document)
                    .thirdPartyName(name)
                    .openingBalance(startBalance)
                    .transactions(transactionDTOs)
                    .totalDebits(totalDebits)
                    .totalCredits(totalCredits)
                    .closingBalance(finalBalance)
                    .totalRecords(transactionDTOs.size())
                    .build());
        }

        // When filtered to one third party, its name comes from whatever
        // group we just built for it (there can be at most one).
        String resolvedTpName = (tpFilter != null && !groups.isEmpty())
                ? groups.get(0).getThirdPartyName()
                : null;

        // The cost center filter's display name isn't carried by any of
        // the rows above (they're grouped by third party, not cost
        // center), so resolve it the same simple way the frontend
        // already does: from whichever line actually used it, if any.
        String resolvedCcName = null;
        if (ccFilter != null) {
            resolvedCcName = allItems.stream()
                    .filter(i -> i.getCostCenter() != null && ccFilter.equals(i.getCostCenter().getCode()))
                    .map(i -> i.getCostCenter().getName())
                    .findFirst()
                    .orElse(null);
        }

        return ThirdPartyBalanceReport.builder()
                .companyName(company.getLegalName())
                .reportTitle("ESTADO DE CUENTA POR TERCERO")
                .startDate(startDate)
                .endDate(endDate)
                .generatedAt(LocalDateTime.now())
                .startAccountCode(startCode)
                .endAccountCode(endCode)
                .thirdPartyDocument(tpFilter)
                .thirdPartyName(resolvedTpName)
                .costCenterCode(ccFilter)
                .costCenterName(resolvedCcName)
                .thirdPartyGroups(groups)
                .build();
    }

    /**
     * Rebuilds a ThirdParty's display name from the raw name-component
     * columns an opening-balance row carries (see the repository query
     * Javadoc for why those columns, not a single name field, are
     * selected), by feeding them into a transient, never-persisted
     * ThirdParty instance and calling its own getLegalDisplayName() --
     * reusing that exact logic instead of duplicating it here.
     */
    private String buildDisplayNameFromRow(Object[] row) {
        ThirdParty transientThirdParty = new ThirdParty();
        transientThirdParty.setBusinessName((String) row[2]);
        transientThirdParty.setFirstName((String) row[3]);
        transientThirdParty.setMiddleName((String) row[4]);
        transientThirdParty.setLastName((String) row[5]);
        transientThirdParty.setSecondLastName((String) row[6]);
        transientThirdParty.setTradeName((String) row[7]);
        return transientThirdParty.getLegalDisplayName();
    }

    /**
     * Running balance for a third party's statement of account.
     *
     * DELIBERATELY NOT nature-aware, unlike CostCenterReportService's
     * version of this same method: a third party's balance behaves like
     * a receivable/payable subledger, always "previous balance + debit -
     * credit", regardless of which account (expense, revenue, tax,
     * cartera...) the line actually posted to. Confirmed with the user
     * (2026-09-25) after an initial nature-aware version -- copied from
     * the Cost Center report's pattern -- produced a balance the user
     * correctly flagged as not matching "saldo inicial + debitos -
     * creditos".
     */
    private List<AuxiliaryLedgerTransaction> processRunningBalance(
            List<JournalEntryItem> items,
            BigDecimal startBalance) {

        List<AuxiliaryLedgerTransaction> dtos = new ArrayList<>();
        BigDecimal currentBalance = startBalance;

        for (JournalEntryItem item : items) {
            BigDecimal debit = item.getDebit() != null ? item.getDebit() : BigDecimal.ZERO;
            BigDecimal credit = item.getCredit() != null ? item.getCredit() : BigDecimal.ZERO;

            currentBalance = currentBalance.add(debit).subtract(credit);

            dtos.add(AuxiliaryLedgerTransaction.builder()
                    .transactionDate(item.getJournalEntry().getEntryDate())
                    .documentNumber(item.getJournalEntry().getDocumentNumber())
                    .detail(item.getDescription() != null
                            ? item.getDescription()
                            : item.getJournalEntry().getDescription())
                    .debit(debit)
                    .credit(credit)
                    .newBalance(currentBalance)
                    .accountCode(item.getAccount().getCode())
                    .accountName(item.getAccount().getName())
                    .costCenterCode(item.getCostCenter() != null ? item.getCostCenter().getCode() : null)
                    .build());
        }

        return dtos;
    }
}
