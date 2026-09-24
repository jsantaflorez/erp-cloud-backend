package com.erp.erp_cloud.service.reports.financial;

import com.erp.erp_cloud.dto.reports.financial.AuxiliaryLedgerTransaction;
import com.erp.erp_cloud.dto.reports.financial.CostCenterBalanceGroup;
import com.erp.erp_cloud.dto.reports.financial.CostCenterBalanceReport;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.JournalEntryItem;
import com.erp.erp_cloud.enums.AccountNature;
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
public class CostCenterReportService extends TenantAwareService {

    private final JournalEntryRepository journalEntryRepository;

    /**
     * Generates the "Auxiliar por Centro de Costo" report: opening
     * balance, transactions and closing balance per cost center within a
     * date range.
     *
     * Same live-aggregation approach as the other reports (no stored
     * balances) -- see the repository query Javadoc for why the
     * opening-balance calculation has to apply each line's own account
     * nature individually, instead of one nature per cost center the way
     * the per-account Auxiliary Ledger does it.
     *
     * @param startDate      start of the reporting period
     * @param endDate        end of the reporting period
     * @param startCode      first account code in range (default "1")
     * @param endCode        last account code in range (default "9999999999")
     * @param costCenterCode optional: restrict to one cost center; null/blank = all
     */
    public CostCenterBalanceReport getCostCenterBalanceReport(
            LocalDate startDate,
            LocalDate endDate,
            String startCode,
            String endCode,
            String costCenterCode) {

        Long companyId = currentTenantId();
        Company company = TenantContext.getCurrentCompany();

        String ccFilter = (costCenterCode == null || costCenterCode.isBlank()) ? null : costCenterCode;

        // 1. Opening balances per cost center (nature already resolved
        // per-line inside the query -- see its Javadoc). One query call,
        // reused to build both the balance map and the name map below.
        List<Object[]> openingBalanceRows = journalEntryRepository
                .getCostCenterOpeningBalances(companyId, startDate, startCode, endCode, ccFilter);

        Map<String, BigDecimal> openingBalancesMap = openingBalanceRows.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (BigDecimal) row[2]
                ));

        Map<String, String> namesFromOpeningBalances = openingBalanceRows.stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (String) row[1], (a, b) -> a));

        // 2. Transaction items for the period.
        List<JournalEntryItem> allItems = journalEntryRepository.findItemsForCostCenterAuxiliary(
                companyId, startDate, endDate, startCode, endCode, ccFilter);

        Map<String, List<JournalEntryItem>> itemsByCostCenter = allItems.stream()
                .collect(Collectors.groupingBy(item -> item.getCostCenter().getCode()));

        // 3. Union of cost center codes from either source.
        Set<String> allCostCenterCodes = new HashSet<>();
        allCostCenterCodes.addAll(openingBalancesMap.keySet());
        allCostCenterCodes.addAll(itemsByCostCenter.keySet());

        // 4. Build one group per cost center.
        List<CostCenterBalanceGroup> groups = new ArrayList<>();

        for (String code : allCostCenterCodes.stream().sorted().toList()) {
            BigDecimal startBalance = openingBalancesMap.getOrDefault(code, BigDecimal.ZERO);
            List<JournalEntryItem> items = itemsByCostCenter.getOrDefault(code, new ArrayList<>());

            if (startBalance.compareTo(BigDecimal.ZERO) == 0 && items.isEmpty()) {
                continue;
            }

            String name = namesFromOpeningBalances.get(code);
            if (name == null && !items.isEmpty()) {
                name = items.get(0).getCostCenter().getName();
            }

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

            groups.add(CostCenterBalanceGroup.builder()
                    .costCenterCode(code)
                    .costCenterName(name)
                    .openingBalance(startBalance)
                    .transactions(transactionDTOs)
                    .totalDebits(totalDebits)
                    .totalCredits(totalCredits)
                    .closingBalance(finalBalance)
                    .totalRecords(transactionDTOs.size())
                    .build());
        }

        // When filtered to one cost center, its name comes from whatever
        // group we just built for it (there can be at most one). No extra
        // lookup needed -- CostCenterRepository has no findByCode method,
        // and adding one just for a display label isn't worth it.
        String resolvedCcName = (ccFilter != null && !groups.isEmpty())
                ? groups.get(0).getCostCenterName()
                : null;

        return CostCenterBalanceReport.builder()
                .companyName(company.getLegalName())
                .reportTitle("AUXILIAR POR CENTRO DE COSTO")
                .startDate(startDate)
                .endDate(endDate)
                .generatedAt(LocalDateTime.now())
                .startAccountCode(startCode)
                .endAccountCode(endCode)
                .costCenterCode(ccFilter)
                .costCenterName(resolvedCcName)
                .costCenterGroups(groups)
                .build();
    }

    /**
     * Same running-balance mechanics as AuxiliaryLedgerService, but the
     * Debit/Credit "nature" is read from EACH ITEM'S OWN account instead
     * of being passed in once for the whole group -- required here
     * because a single cost center's items can come from accounts with
     * different natures (unlike a per-account group, where every item
     * necessarily shares that one account's nature).
     */
    private List<AuxiliaryLedgerTransaction> processRunningBalance(
            List<JournalEntryItem> items,
            BigDecimal startBalance) {

        List<AuxiliaryLedgerTransaction> dtos = new ArrayList<>();
        BigDecimal currentBalance = startBalance;

        for (JournalEntryItem item : items) {
            BigDecimal debit = item.getDebit() != null ? item.getDebit() : BigDecimal.ZERO;
            BigDecimal credit = item.getCredit() != null ? item.getCredit() : BigDecimal.ZERO;
            AccountNature nature = item.getAccount().getNature();

            if (AccountNature.D.equals(nature)) {
                currentBalance = currentBalance.add(debit).subtract(credit);
            } else {
                currentBalance = currentBalance.add(credit).subtract(debit);
            }

            dtos.add(AuxiliaryLedgerTransaction.builder()
                    .transactionDate(item.getJournalEntry().getEntryDate())
                    .documentNumber(item.getJournalEntry().getDocumentNumber())
                    .detail(item.getDescription() != null
                            ? item.getDescription()
                            : item.getJournalEntry().getDescription())
                    .debit(debit)
                    .credit(credit)
                    .newBalance(currentBalance)
                    .thirdPartyDocument(item.getThirdParty() != null
                            ? item.getThirdParty().getDocumentNumber()
                            : null)
                    .thirdPartyName(item.getThirdParty() != null
                            ? item.getThirdParty().getLegalDisplayName()
                            : null)
                    .accountCode(item.getAccount().getCode())
                    .accountName(item.getAccount().getName())
                    .build());
        }

        return dtos;
    }
}
