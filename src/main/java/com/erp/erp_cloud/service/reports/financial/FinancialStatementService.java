package com.erp.erp_cloud.service.reports.financial;

import com.erp.erp_cloud.dto.reports.financial.*;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.enums.AccountCategory;
import com.erp.erp_cloud.enums.AccountClass;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.security.context.TenantContext;
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
    private final TenantContext companyContext;

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
// CALCULATION HELPERS (Polymorphic - works with any FinancialSection)
// ═══════════════════════════════════════════════════════════

    /**
     * Calculates the total of all financial sections using polymorphism.
     *
     * Works with ANY implementation of FinancialSection:
     * - BalanceSheetSection
     * - IncomeStatementSection
     * - CashFlowSection (future)
     *
     * No instanceof checks needed - pure polymorphism!
     *
     * @param sections List of any FinancialSection implementation
     * @return Sum of all section totals
     */
    private <T extends FinancialSection> BigDecimal calculateTotal(List<T> sections) {
        if (sections == null || sections.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return sections.stream()
                .map(FinancialSection::getSectionTotal)  // ← Polymorphic call!
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


    // ═══════════════════════════════════════════════════════════
    // INCOME STATEMENT (//* ESTADOS DE RESULTADO)
    // ═══════════════════════════════════════════════════════════

    /**
     * Generates an Income Statement (Estado de Resultados) for a date range.
     *
     * The Income Statement shows:
     * - Revenue (what the company earned)
     * - Costs (direct costs of goods/services sold)
     * - Expenses (operating and non-operating expenses)
     * - Net Income (profit or loss)
     *
     * Formula: Revenue - Costs - Expenses - Taxes = Net Income
     *
     * Key Metrics:
     * - Gross Profit = Revenue - Costs
     * - Operating Income = Gross Profit - Operating Expenses
     * - Net Income = Operating Income - Non-Operating Expenses - Taxes
     *
     * @param startDate Start of the reporting period
     * @param endDate End of the reporting period
     * @return Complete Income Statement with all sections and metrics
     */
    public IncomeStatementReport getIncomeStatement(LocalDate startDate, LocalDate endDate) {
        Company company = companyContext.getCurrentCompany();

        log.info("Generating Income Statement for company: {} from {} to {}",
                company.getId(), startDate, endDate);

        // Validation
        if (startDate == null || endDate == null) {
            throw new InvalidOperationException("Start date and end date are required for Income Statement");
        }

        if (startDate.isAfter(endDate)) {
            throw new InvalidOperationException("Start date cannot be after end date");
        }

        if (endDate.isAfter(LocalDate.now())) {
            throw new InvalidOperationException("Cannot generate Income Statement for future dates");
        }

        // ═══════════════════════════════════════════════════════════
        // STEP 1: Build Revenue Sections
        // ═══════════════════════════════════════════════════════════

        List<IncomeStatementSection> revenueSections = buildSectionsForIncomeStatement(
                company, AccountClass.REVENUE, startDate, endDate
        );

        BigDecimal totalRevenue = calculateTotal(revenueSections);

        log.debug("Total Revenue: {}", totalRevenue);

        // ═══════════════════════════════════════════════════════════
        // STEP 2: Build Cost Sections
        // ═══════════════════════════════════════════════════════════

        List<IncomeStatementSection> costSections = buildSectionsForIncomeStatement(
                company, AccountClass.COST, startDate, endDate
        );

        BigDecimal totalCosts = calculateTotal(costSections);

        log.debug("Total Costs: {}", totalCosts);

        // ═══════════════════════════════════════════════════════════
        // STEP 3: Calculate Gross Profit
        // ═══════════════════════════════════════════════════════════

        BigDecimal grossProfit = totalRevenue.subtract(totalCosts);

        log.debug("Gross Profit: {}", grossProfit);

        // ═══════════════════════════════════════════════════════════
        // STEP 4: Build Expense Sections (Separate Operating from Non-Operating)
        // ═══════════════════════════════════════════════════════════

        List<IncomeStatementSection> allExpenseSections = buildSectionsForIncomeStatement(
                company, AccountClass.EXPENSE, startDate, endDate
        );

        // Separate operating from non-operating and tax expenses
        List<IncomeStatementSection> operatingExpenseSections = new ArrayList<>();
        List<IncomeStatementSection> nonOperatingExpenseSections = new ArrayList<>();
        List<IncomeStatementSection> taxExpenseSections = new ArrayList<>();

        for (IncomeStatementSection section : allExpenseSections) {
            // Check the category to determine where it goes
            String sectionName = section.getSectionName();

            if (sectionName.contains("Tax")) {
                taxExpenseSections.add(section);
            } else if (sectionName.contains("Financial") ||
                    sectionName.contains("Non-operating") ||
                    sectionName.contains("Other Expense")) {
                nonOperatingExpenseSections.add(section);
            } else {
                operatingExpenseSections.add(section);
            }
        }

        BigDecimal totalOperatingExpenses = calculateTotal(operatingExpenseSections);
        BigDecimal totalNonOperatingExpenses = calculateTotal(nonOperatingExpenseSections);
        BigDecimal totalTaxExpenses = calculateTotal(taxExpenseSections);

        log.debug("Operating Expenses: {}, Non-Operating: {}, Taxes: {}",
                totalOperatingExpenses, totalNonOperatingExpenses, totalTaxExpenses);

        // ═══════════════════════════════════════════════════════════
        // STEP 5: Calculate Key Subtotals
        // ═══════════════════════════════════════════════════════════

        // Operating Income (EBIT) = Gross Profit - Operating Expenses
        BigDecimal operatingIncome = grossProfit.subtract(totalOperatingExpenses);

        // Income Before Taxes (EBT) = Operating Income - Non-Operating Expenses
        BigDecimal incomeBeforeTaxes = operatingIncome.subtract(totalNonOperatingExpenses);

        // Net Income = Income Before Taxes - Tax Expenses
        BigDecimal netIncome = incomeBeforeTaxes.subtract(totalTaxExpenses);

        log.debug("Operating Income: {}, Income Before Taxes: {}, Net Income: {}",
                operatingIncome, incomeBeforeTaxes, netIncome);

        // ═══════════════════════════════════════════════════════════
        // STEP 6: Calculate Financial Metrics (Margins)
        // ═══════════════════════════════════════════════════════════

        BigDecimal grossProfitMargin = calculateMargin(grossProfit, totalRevenue);
        BigDecimal operatingMargin = calculateMargin(operatingIncome, totalRevenue);
        BigDecimal netProfitMargin = calculateMargin(netIncome, totalRevenue);

        log.debug("Margins - Gross: {}%, Operating: {}%, Net: {}%",
                grossProfitMargin, operatingMargin, netProfitMargin);

        // ═══════════════════════════════════════════════════════════
        // STEP 7: Build and Return Report
        // ═══════════════════════════════════════════════════════════

        IncomeStatementReport report = IncomeStatementReport.builder()
                .companyName(company.getLegalName())
                .startDate(startDate)
                .endDate(endDate)
                .generatedAt(LocalDate.now())

                // Revenue
                .revenueSections(revenueSections)
                .totalRevenue(totalRevenue.setScale(2, RoundingMode.HALF_UP))

                // Costs
                .costSections(costSections)
                .totalCosts(totalCosts.setScale(2, RoundingMode.HALF_UP))
                .grossProfit(grossProfit.setScale(2, RoundingMode.HALF_UP))

                // Expenses
                .operatingExpenseSections(operatingExpenseSections)
                .totalOperatingExpenses(totalOperatingExpenses.setScale(2, RoundingMode.HALF_UP))
                .operatingIncome(operatingIncome.setScale(2, RoundingMode.HALF_UP))

                .nonOperatingExpenseSections(nonOperatingExpenseSections)
                .totalNonOperatingExpenses(totalNonOperatingExpenses.setScale(2, RoundingMode.HALF_UP))
                .incomeBeforeTaxes(incomeBeforeTaxes.setScale(2, RoundingMode.HALF_UP))

                .taxExpenseSections(taxExpenseSections)
                .totalTaxExpenses(totalTaxExpenses.setScale(2, RoundingMode.HALF_UP))

                // Net Income
                .netIncome(netIncome.setScale(2, RoundingMode.HALF_UP))

                // Metrics
                .grossProfitMargin(grossProfitMargin.setScale(1, RoundingMode.HALF_UP))
                .operatingMargin(operatingMargin.setScale(1, RoundingMode.HALF_UP))
                .netProfitMargin(netProfitMargin.setScale(1, RoundingMode.HALF_UP))

                .build();

        log.info("Income Statement generated successfully. Net Income: {} ({}%)",
                netIncome, netProfitMargin);

        return report;
    }

    // ═══════════════════════════════════════════════════════════
    // HELPER METHODS FOR INCOME STATEMENT
    // ═══════════════════════════════════════════════════════════

    /**
     * Builds sections for a specific account class (Revenue, Cost, or Expense).
     * Groups accounts by category and creates sections.
     */
    private List<IncomeStatementSection> buildSectionsForIncomeStatement(
            Company company,
            AccountClass accountClass,
            LocalDate startDate,
            LocalDate endDate) {

        // Get all accounts for this class with their period balances
        List<Object[]> accounts = chartOfAccountsRepository.getAccountsForIncomeStatement(
                company,
                accountClass,
                startDate,
                endDate
        );

        // Group accounts by category
        Map<AccountCategory, List<IncomeStatementSection.AccountLine>> categorizedAccounts = new LinkedHashMap<>();

        for (Object[] row : accounts) {
            try {
                String accountCode = (String) row[0];
                String accountName = (String) row[1];
                AccountCategory category = (AccountCategory) row[2];
                Integer displayOrder = row[3] != null ? (Integer) row[3] : 999;
                BigDecimal periodBalance = row[4] != null ? (BigDecimal) row[4] : BigDecimal.ZERO;

                // Only include accounts with non-zero balances
                if (periodBalance.compareTo(BigDecimal.ZERO) != 0) {
                    IncomeStatementSection.AccountLine line = IncomeStatementSection.AccountLine.builder()
                            .accountCode(accountCode)
                            .accountName(accountName)
                            .amount(periodBalance.abs()) // Always show as positive
                            .build();

                    categorizedAccounts
                            .computeIfAbsent(category, k -> new ArrayList<>())
                            .add(line);
                }
            } catch (Exception e) {
                log.error("Error processing account data from row", e);
            }
        }

        // Build sections from categorized accounts
        List<IncomeStatementSection> sections = new ArrayList<>();

        for (Map.Entry<AccountCategory, List<IncomeStatementSection.AccountLine>> entry : categorizedAccounts.entrySet()) {
            AccountCategory category = entry.getKey();
            List<IncomeStatementSection.AccountLine> lines = entry.getValue();

            BigDecimal sectionTotal = lines.stream()
                    .map(IncomeStatementSection.AccountLine::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            IncomeStatementSection section = IncomeStatementSection.builder()
                    .sectionName(category.getDisplayName())
                    .sectionNameEs(category.getDisplayNameEs())
                    .accountLines(lines)
                    .sectionTotal(sectionTotal.setScale(2, RoundingMode.HALF_UP))
                    .displayOrder(category.getDisplayOrder())
                    .build();

            sections.add(section);
        }

        // Sort by display order
        sections.sort(Comparator.comparing(IncomeStatementSection::getDisplayOrder));

        return sections;
    }

//    /**
//     * Calculates the total of all sections.
//     */
//    private BigDecimal calculateTotal(List<IncomeStatementSection> sections) {
//        if (sections == null || sections.isEmpty()) {
//            return BigDecimal.ZERO;
//        }
//
//        return sections.stream()
//                .map(IncomeStatementSection::getSectionTotal)
//                .reduce(BigDecimal.ZERO, BigDecimal::add)
//                .setScale(2, RoundingMode.HALF_UP);
//    }

    /**
     * Calculates a percentage margin.
     *
     * Formula: (numerator / denominator) × 100
     *
     * Example: Gross Profit Margin = (Gross Profit / Revenue) × 100
     *
     * @param numerator The top part of the ratio (e.g., Gross Profit)
     * @param denominator The bottom part of the ratio (e.g., Revenue)
     * @return Percentage as BigDecimal (e.g., 60.5 for 60.5%)
     */

    private BigDecimal calculateMargin(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        if (numerator == null) {
            return BigDecimal.ZERO;
        }

        return numerator
                .divide(denominator, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(1, RoundingMode.HALF_UP);
    }

    // Note: verifyAccountingEquation and other Balance Sheet methods remain unchanged
}



