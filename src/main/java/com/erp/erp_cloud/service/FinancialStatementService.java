package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.reports.financial.BalanceSheetReport;
import com.erp.erp_cloud.dto.reports.financial.BalanceSheetSection;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.enums.AccountCategory;
import com.erp.erp_cloud.enums.AccountClass;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.security.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinancialStatementService {

    private static final Logger log = LoggerFactory.getLogger(FinancialStatementService.class);

    private final JournalEntryRepository journalEntryRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final CompanyContext companyContext;

    // ═══════════════════════════════════════════════════════════
    // BALANCE SHEET
    // ═══════════════════════════════════════════════════════════

    /**
     * Generates a Balance Sheet (Estado de Situación Financiera) as of a specific date.
     *
     * The Balance Sheet shows:
     * - Assets (what the company owns)
     * - Liabilities (what the company owes)
     * - Equity (owner's stake in the company)
     *
     * ACCOUNTING EQUATION: Assets = Liabilities + Equity
     *
     * @param asOfDate The date for which to generate the balance sheet
     * @return Complete balance sheet with all sections and totals
     */
    public BalanceSheetReport getBalanceSheet(LocalDate asOfDate) {
        Company company = companyContext.getCurrentCompany();

        log.info("Generating Balance Sheet for company: {} as of {}", company.getId(), asOfDate);

        if (asOfDate == null) {
            throw new InvalidOperationException("Balance Sheet date cannot be null");
        }

        if (asOfDate.isAfter(LocalDate.now())) {
            throw new InvalidOperationException("Cannot generate Balance Sheet for future date: " + asOfDate);
        }

        // 1. Get account balances as of the specified date
        Map<String, BigDecimal> accountBalances = getAccountBalances(company, asOfDate);

        // 2. Build Asset sections
        List<BalanceSheetSection> assetSections = buildAssetSections(accountBalances);
        BigDecimal totalAssets = calculateTotal(assetSections);

        // 3. Build Liability sections
        List<BalanceSheetSection> liabilitySections = buildLiabilitySections(accountBalances);
        BigDecimal totalLiabilities = calculateTotal(liabilitySections);

        // 4. Build Equity sections
        List<BalanceSheetSection> equitySections = buildEquitySections(accountBalances);
        BigDecimal totalEquity = calculateTotal(equitySections);

        // 5. Verify the accounting equation
        boolean isBalanced = verifyAccountingEquation(totalAssets, totalLiabilities, totalEquity);

        if (!isBalanced) {
            log.error("BALANCE SHEET OUT OF BALANCE! Assets: {}, Liabilities: {}, Equity: {}",
                    totalAssets, totalLiabilities, totalEquity);
            // In production, you might want to throw an exception here
        }

        // 6. Build and return the report
        BalanceSheetReport report = BalanceSheetReport.builder()
                .companyName(company.getLegalName())
                .asOfDate(asOfDate)
                .assetSections(assetSections)
                .liabilitySections(liabilitySections)
                .equitySections(equitySections)
                .totalAssets(totalAssets)
                .totalLiabilities(totalLiabilities)
                .totalEquity(totalEquity)
                .totalLiabilitiesAndEquity(totalLiabilities.add(totalEquity))
                .isBalanced(isBalanced)
                .generatedAt(LocalDate.now())
                .build();

        log.info("Balance Sheet generated successfully. Assets: {}, L+E: {}, Balanced: {}",
                totalAssets, totalLiabilities.add(totalEquity), isBalanced);

        return report;
    }

    // ═══════════════════════════════════════════════════════════
    // ACCOUNT BALANCE CALCULATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Calculates the balance for each account as of a specific date.
     *
     * Balance calculation:
     * - For Debit accounts (Assets, Expenses): Balance = Total Debits - Total Credits
     * - For Credit accounts (Liabilities, Equity, Revenue): Balance = Total Credits - Total Debits
     *
     * @param company The company
     * @param asOfDate Calculate balances up to and including this date
     * @return Map of account code to balance
     */
    private Map<String, BigDecimal> getAccountBalances(Company company, LocalDate asOfDate) {
        log.debug("Calculating account balances for company: {} as of {}", company.getId(), asOfDate);

        List<Object[]> balances = journalEntryRepository.getAccountBalancesAsOfDate(company, asOfDate);

        Map<String, BigDecimal> accountBalances = new HashMap<>();

        for (Object[] row : balances) {
            String accountCode = (String) row[0];
            String accountNature = (String) row[1];

            // Add null checks
            BigDecimal totalDebit = row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO;
            BigDecimal totalCredit = row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO;

            BigDecimal balance;
            if ("D".equals(accountNature)) {
                balance = totalDebit.subtract(totalCredit);
            } else {
                balance = totalCredit.subtract(totalDebit);
            }

            accountBalances.put(accountCode, balance.setScale(2, RoundingMode.HALF_UP));
        }

        log.debug("Calculated balances for {} accounts", accountBalances.size());

        return accountBalances;
    }

    // ═══════════════════════════════════════════════════════════
    // SECTION BUILDERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Builds the Asset sections of the Balance Sheet.
     * Groups accounts by category (Current Assets, Fixed Assets, etc.)
     */
    private List<BalanceSheetSection> buildAssetSections(Map<String, BigDecimal> accountBalances) {
        return buildSectionsForClass(AccountClass.ASSET, accountBalances);
    }

    /**
     * Builds the Liability sections of the Balance Sheet.
     */
    private List<BalanceSheetSection> buildLiabilitySections(Map<String, BigDecimal> accountBalances) {
        return buildSectionsForClass(AccountClass.LIABILITY, accountBalances);
    }

    /**
     * Builds the Equity sections of the Balance Sheet.
     */
    private List<BalanceSheetSection> buildEquitySections(Map<String, BigDecimal> accountBalances) {
        return buildSectionsForClass(AccountClass.EQUITY, accountBalances);
    }

    /**
     * Generic method to build sections for a specific account class.
     * Groups accounts by category and calculates subtotals.
     */
    private List<BalanceSheetSection> buildSectionsForClass(
            AccountClass accountClass,
            Map<String, BigDecimal> accountBalances) {

        Company company = companyContext.getCurrentCompany();

        List<Object[]> accounts = chartOfAccountsRepository.getAccountsForBalanceSheet(
                company,
                accountClass
        );

        Map<AccountCategory, List<BalanceSheetSection.AccountLine>> categorizedAccounts = new LinkedHashMap<>();

        for (Object[] row : accounts) {
            try {
                String accountCode = (String) row[0];
                String accountName = (String) row[1];
                AccountCategory category = (AccountCategory) row[2];
                Integer displayOrder = row[3] != null ? (Integer) row[3] : 999;

                BigDecimal balance = accountBalances.getOrDefault(accountCode, BigDecimal.ZERO);

                // Only include accounts with non-zero balances
                if (balance.compareTo(BigDecimal.ZERO) != 0) {
                    BalanceSheetSection.AccountLine line = BalanceSheetSection.AccountLine.builder()
                            .accountCode(accountCode)
                            .accountName(accountName)
                            .balance(balance.abs()) // Always positive in display
                            .build();

                    categorizedAccounts
                            .computeIfAbsent(category, k -> new ArrayList<>())
                            .add(line);
                }
            } catch (ClassCastException e) {
                log.error("Error casting account data from row: {}", row, e);
            }
        }

        // Build sections from categorized accounts
        List<BalanceSheetSection> sections = new ArrayList<>();

        for (Map.Entry<AccountCategory, List<BalanceSheetSection.AccountLine>> entry : categorizedAccounts.entrySet()) {
            AccountCategory category = entry.getKey();
            List<BalanceSheetSection.AccountLine> lines = entry.getValue();

            BigDecimal sectionTotal = lines.stream()
                    .map(BalanceSheetSection.AccountLine::getBalance)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BalanceSheetSection section = BalanceSheetSection.builder()
                    .sectionName(category.getDisplayName())
                    .sectionNameEs(category.getDisplayNameEs())
                    .accountLines(lines)
                    .sectionTotal(sectionTotal)
                    .displayOrder(category.getDisplayOrder())
                    .build();

            sections.add(section);
        }

        sections.sort(Comparator.comparing(BalanceSheetSection::getDisplayOrder));

        return sections;
    }
    // ═══════════════════════════════════════════════════════════
    // CALCULATION HELPERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Calculates the total of all sections.
     */
    private BigDecimal calculateTotal(List<BalanceSheetSection> sections) {
        return sections.stream()
                .map(BalanceSheetSection::getSectionTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Verifies the fundamental accounting equation: Assets = Liabilities + Equity
     *
     * Allows for small rounding differences (up to 0.01)
     */
    private boolean verifyAccountingEquation(
            BigDecimal totalAssets,
            BigDecimal totalLiabilities,
            BigDecimal totalEquity) {

        BigDecimal totalLiabilitiesAndEquity = totalLiabilities.add(totalEquity);
        BigDecimal difference = totalAssets.subtract(totalLiabilitiesAndEquity).abs();

        // Allow for rounding tolerance of 1 cent
        BigDecimal tolerance = new BigDecimal("0.01");

        return difference.compareTo(tolerance) <= 0;
    }
}