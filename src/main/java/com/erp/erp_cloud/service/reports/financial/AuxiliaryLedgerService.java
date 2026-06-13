package com.erp.erp_cloud.service.reports.financial;

import com.erp.erp_cloud.dto.reports.financial.AuxiliaryAccountGroup;
import com.erp.erp_cloud.dto.reports.financial.AuxiliaryLedgerReport;
import com.erp.erp_cloud.dto.reports.financial.AuxiliaryLedgerTransaction;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.JournalEntryItem;
import com.erp.erp_cloud.enums.AccountNature;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
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
public class AuxiliaryLedgerService extends TenantAwareService {

    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final JournalEntryRepository journalEntryRepository;

    /**
     * Generates Auxiliary Ledger Report (Libro Auxiliar por Cuenta).
     *
     * Shows detailed transaction history for a range of accounts with:
     * - Opening balance (before period)
     * - All transactions in chronological order
     * - Running balance after each transaction
     * - Period totals per account
     *
     * @param startDate Start of the reporting period
     * @param endDate End of the reporting period
     * @param startCode First account code in range (e.g., "11")
     * @param endCode Last account code in range (e.g., "1199999999")
     * @return Complete auxiliary ledger report
     */
    public AuxiliaryLedgerReport getAuxiliaryLedgerReport(
            LocalDate startDate,
            LocalDate endDate,
            String startCode,
            String endCode) {

        // Extraemos el ID numérico optimizado del Tenant actual
        Long companyId = currentTenantId();

        // Obtenemos la entidad Company desde el ThreadLocal sin generar queries SQL adicionales
        Company company = TenantContext.getCurrentCompany();

        // 1. Fetch opening balances for all accounts in range (ADAPTED to Long companyId)
        Map<String, BigDecimal> openingBalancesMap = chartOfAccountsRepository
                .getOpeningBalancesForAuxiliary(companyId, startDate, startCode, endCode)
                .stream()
                .collect(Collectors.toMap(
                        obj -> (String) obj[0],
                        obj -> (BigDecimal) obj[1]
                ));

        // 2. Fetch all transaction items for the range (ADAPTED to Long companyId)
        List<JournalEntryItem> allItems = journalEntryRepository.findItemsForAuxiliary(
                companyId, startDate, endDate, startCode, endCode);

        // 3. Group items by account code
        Map<String, List<JournalEntryItem>> itemsByAccount = allItems.stream()
                .collect(Collectors.groupingBy(item -> item.getAccount().getCode()));

        // 4. Get all unique account codes (from opening balances OR transactions)
        Set<String> allAccountCodes = new HashSet<>();
        allAccountCodes.addAll(openingBalancesMap.keySet());
        allAccountCodes.addAll(itemsByAccount.keySet());

        // 5. Process each account
        List<AuxiliaryAccountGroup> accountGroups = new ArrayList<>();

        for (String code : allAccountCodes.stream().sorted().toList()) {
            // Get account metadata (ADAPTED to Long companyId)
            ChartOfAccounts account = chartOfAccountsRepository
                    .findByCompanyIdAndCode(companyId, code)
                    .orElse(null);

            if (account == null) {
                continue; // Skip if account not found
            }

            BigDecimal startBalance = openingBalancesMap.getOrDefault(code, BigDecimal.ZERO);
            List<JournalEntryItem> items = itemsByAccount.getOrDefault(code, new ArrayList<>());

            // Skip accounts with no opening balance AND no transactions
            if (startBalance.compareTo(BigDecimal.ZERO) == 0 && items.isEmpty()) {
                continue;
            }

            // Process running balance for this account
            List<AuxiliaryLedgerTransaction> transactionDTOs = processRunningBalance(
                    items,
                    startBalance,
                    account.getNature()  // ✅ Pass Enum
            );

            // Calculate totals
            BigDecimal totalDebits = transactionDTOs.stream()
                    .map(t -> t.getDebit() != null ? t.getDebit() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalCredits = transactionDTOs.stream()
                    .map(t -> t.getCredit() != null ? t.getCredit() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal finalBalance = transactionDTOs.isEmpty()
                    ? startBalance
                    : transactionDTOs.get(transactionDTOs.size() - 1).getNewBalance();

            // Build account group
            accountGroups.add(AuxiliaryAccountGroup.builder()
                    .accountCode(account.getCode())
                    .accountName(account.getName())
                    .accountNature(account.getNature())  // ✅ Enum
                    .openingBalance(startBalance)
                    .transactions(transactionDTOs)
                    .totalDebits(totalDebits)
                    .totalCredits(totalCredits)
                    .closingBalance(finalBalance)
                    .totalRecords(transactionDTOs.size())
                    .build());
        }

        // 6. Build and return report
        return AuxiliaryLedgerReport.builder()
                .companyName(company.getLegalName())
                .reportTitle("AUXILIAR POR CUENTA - RESUMIDO")
                .startDate(startDate)
                .endDate(endDate)
                .generatedAt(LocalDateTime.now())
                .startAccountCode(startCode)
                .endAccountCode(endCode)
                .accountGroups(accountGroups)
                .pageNumber(1)
                .totalPages(1)
                .build();
    }

    /**
     * Processes transactions and calculates running balance.
     */
    private List<AuxiliaryLedgerTransaction> processRunningBalance(
            List<JournalEntryItem> items,
            BigDecimal startBalance,
            AccountNature nature) {

        List<AuxiliaryLedgerTransaction> dtos = new ArrayList<>();
        BigDecimal currentBalance = startBalance;

        for (JournalEntryItem item : items) {
            BigDecimal debit = item.getDebit() != null ? item.getDebit() : BigDecimal.ZERO;
            BigDecimal credit = item.getCredit() != null ? item.getCredit() : BigDecimal.ZERO;

            // Apply Colombian accounting logic per account nature
            if (AccountNature.D.equals(nature)) {
                currentBalance = currentBalance.add(debit).subtract(credit);
            } else {
                currentBalance = currentBalance.add(credit).subtract(debit);
            }

            // Build transaction DTO
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
                    .costCenterCode(item.getCostCenter() != null
                            ? item.getCostCenter().getCode()
                            : null)
                    .build());
        }

        return dtos;
    }
}