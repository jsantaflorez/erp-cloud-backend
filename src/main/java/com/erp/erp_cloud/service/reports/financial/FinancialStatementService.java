package com.erp.erp_cloud.service.reports.financial;

import com.erp.erp_cloud.dto.reports.financial.*;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.enums.AccountCategory;
import com.erp.erp_cloud.enums.AccountClass;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.repository.AccountingPeriodRepository;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.security.context.TenantContext;
import com.erp.erp_cloud.service.base.TenantAwareService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinancialStatementService extends TenantAwareService {

    private static final Logger log = LoggerFactory.getLogger(FinancialStatementService.class);

    private final JournalEntryRepository journalEntryRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;

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
        // Obtenemos la entidad Company desde el ThreadLocal sin generar queries SQL adicionales
        Company company = TenantContext.getCurrentCompany();
        Long companyId = currentTenantId();

        log.info("Generating Balance Sheet for company ID: {} as of {}", companyId, asOfDate);

        if (asOfDate == null) {
            throw new InvalidOperationException("Balance Sheet date cannot be null");
        }

        if (asOfDate.isAfter(LocalDate.now())) {
            throw new InvalidOperationException("Cannot generate Balance Sheet for future date: " + asOfDate);
        }

        // 1. Get account balances as of the specified date (ADAPTED to Long companyId)
        Map<String, BigDecimal> accountBalances = getAccountBalances(companyId, asOfDate);

        // 2. Build Asset sections
        List<BalanceSheetSection> assetSections = buildAssetSections(accountBalances);
        BigDecimal totalAssets = calculateTotal(assetSections);

        // 3. Build Liability sections
        List<BalanceSheetSection> liabilitySections = buildLiabilitySections(accountBalances);
        BigDecimal totalLiabilities = calculateTotal(liabilitySections);

        // 4. Build Equity sections
        List<BalanceSheetSection> equitySections = buildEquitySections(accountBalances);

        // 4b. If the fiscal year is still open, Patrimonio does not yet
        // include the current period's net income/loss -- it only lands
        // there via the year-end closing entry (AccountingPeriodService.
        // closeYear()). Append a computed "Resultado del Ejercicio (sin
        // cerrar)" line so the Balance Sheet balances correctly even
        // before closing -- see appendUnclosedCurrentYearResult() for
        // the full rationale.
        appendUnclosedCurrentYearResult(equitySections, companyId, asOfDate);

        BigDecimal totalEquity = calculateTotal(equitySections);

        // 5. Verify the accounting equation
        boolean isBalanced = verifyAccountingEquation(totalAssets, totalLiabilities, totalEquity);

        if (!isBalanced) {
            log.error("BALANCE SHEET OUT OF BALANCE! Assets: {}, Liabilities: {}, Equity: {}",
                    totalAssets, totalLiabilities, totalEquity);
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
                .balanced(isBalanced)
                .generatedAt(LocalDateTime.now())
                .build();

        log.info("Balance Sheet generated successfully. Assets: {}, L+E: {}, Balanced: {}",
                totalAssets, totalLiabilities.add(totalEquity), isBalanced);

        return report;
    }

    // ═══════════════════════════════════════════════════════════
    // ACCOUNT BALANCE CALCULATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Calculates the RAW balance for each account as of a specific date,
     * in a uniform debit-positive convention (debit - credit, always --
     * never flipped per the account's own `nature`).
     *
     * BUG FIX (2026-10-10): this used to flip the sign per-account based
     * on its own `nature` (D/C), so every account came back positive
     * when it held its own "normal" balance. That broke the moment a
     * class mixed accounts of opposite nature -- e.g. Pasivos (24)
     * holding both "IVA Generado" (nature C, a real liability) and "IVA
     * Descontable" (nature D, a contra-liability that should REDUCE the
     * net payable). Both came back positive and got added together in
     * buildSectionsForClass(), so the Balance Sheet showed Pasivos as
     * debits+credits instead of debits-credits, overstating Pasivos and
     * breaking Activo = Pasivo + Patrimonio (reported by the user while
     * testing the 2025 year-end close, comparing against the Balance de
     * Comprobación Detallado and the legacy SIEWIN report, both of which
     * correctly NET the two sub-accounts instead of adding them).
     *
     * The fix: keep this map nature-agnostic (same uniform convention
     * already proven correct in JournalEntryService.getTrialBalanceDetailed(),
     * which nets a header account's children by simple debit-minus-credit
     * rollup regardless of each leaf's own nature) and let
     * buildSectionsForClass() orient the sign once, per SECTION class,
     * not per individual account.
     *
     * @param companyId The active tenant primitive ID
     * @param asOfDate Calculate balances up to and including this date
     * @return Map of account code to raw (debit-positive) balance
     */
    private Map<String, BigDecimal> getAccountBalances(Long companyId, LocalDate asOfDate) {
        log.debug("Calculating account balances for company ID: {} as of {}", companyId, asOfDate);

        // ADAPTED: Calls repository using the numeric company ID parameter
        List<Object[]> balances = journalEntryRepository.getAccountBalancesAsOfDate(companyId, asOfDate);

        Map<String, BigDecimal> accountBalances = new HashMap<>();

        for (Object[] row : balances) {
            String accountCode = (String) row[0];
            // row[1] (account nature) is no longer used for sign here --
            // see buildSectionsForClass(), which applies the sign based
            // on the SECTION's class instead of the individual account.

            // Add null checks
            BigDecimal totalDebit = row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO;
            BigDecimal totalCredit = row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO;

            BigDecimal balance = totalDebit.subtract(totalCredit);

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
     * Appends a computed "Resultado del Ejercicio (sin cerrar)" line to
     * Patrimonio when the fiscal year of {@code asOfDate} has not been
     * closed yet.
     *
     * WHY THIS EXISTS (2026-10-12, reported by the user): Patrimonio only
     * picks up the current year's net income/loss once the year-end
     * closing entry posts it there (see AccountingPeriodService.closeYear(),
     * which debits/credits the Ganancia/Pérdida account the company
     * configured). Before that closing entry exists, Activo legitimately
     * does NOT equal Pasivo + Patrimonio by exactly the period's net
     * income -- that is not a data error, it is the normal state of an
     * open fiscal year in any accounting system (the legacy SIEWIN
     * included). The user hit this while reviewing the test company's
     * Balance General: it flagged "Fuera de Balance" for a $2,000,000
     * difference that was simply Ingresos $12,000,000 - Gastos
     * $10,000,000 not yet closed into Patrimonio -- alarming, since it
     * reads as unbalanced journal entries when double-entry was fine.
     *
     * Rather than just softening the alarm (e.g. a "temporary
     * difference" banner), this computes the actual Resultado del
     * Ejercicio and adds it to Patrimonio, exactly like
     * BalanceSheetReport's own equitySections Javadoc already anticipated
     * ("Share Capital, Retained Earnings, Current Year Profit") -- so the
     * Balance Sheet balances correctly all year round, and the "Fuera de
     * Balance" alarm stays reserved for genuine errors (like the
     * Patrimonio sign-reversal bug fixed earlier this same week).
     *
     * Reuses getIncomeStatement() rather than recomputing revenue/cost/
     * expense totals here, so this always stays in sync with the Income
     * Statement's own calculation.
     *
     * ASSUMPTION: fiscal years are closed in order, as enforced by
     * closeYear()'s validatePriorYearClosedIfExists() -- there is never
     * more than one open year at a time in the normal workflow, so
     * checking only asOfDate's own year is sufficient.
     *
     * @param equitySections mutable list this method appends to (no-op if the year is closed or net income is zero)
     * @param companyId the active tenant's primitive ID
     * @param asOfDate the Balance Sheet's cutoff date
     */
    private void appendUnclosedCurrentYearResult(
            List<BalanceSheetSection> equitySections,
            Long companyId,
            LocalDate asOfDate) {

        int year = asOfDate.getYear();
        boolean yearClosed = accountingPeriodRepository.existsByCompanyIdAndYearAndYearCloseTrue(companyId, year);
        if (yearClosed) {
            return;
        }

        LocalDate yearStart = LocalDate.of(year, 1, 1);
        BigDecimal netIncome = getIncomeStatement(yearStart, asOfDate).getNetIncome();

        if (netIncome.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        boolean isProfit = netIncome.compareTo(BigDecimal.ZERO) > 0;

        BalanceSheetSection.AccountLine line = BalanceSheetSection.AccountLine.builder()
                .accountCode("")
                .accountName(isProfit
                        ? "Utilidad del Ejercicio (sin cerrar)"
                        : "Pérdida del Ejercicio (sin cerrar)")
                .balance(netIncome.abs())
                .build();

        BalanceSheetSection section = BalanceSheetSection.builder()
                .sectionName(isProfit
                        ? "Net Income (Current Period, Unclosed)"
                        : "Net Loss (Current Period, Unclosed)")
                .sectionNameEs(isProfit
                        ? "Utilidad del Ejercicio (sin cerrar)"
                        : "Pérdida del Ejercicio (sin cerrar)")
                .accountLines(List.of(line))
                .sectionTotal(netIncome)
                .displayOrder(Integer.MAX_VALUE - 1)
                .build();

        equitySections.add(section);
    }

    /**
     * Generic method to build sections for a specific account class.
     *
     * BUG FIX (2026-10-10): sectionTotal used to be a sum of each line's
     * already-abs()'d display value, which silently ADDED any contra
     * account (e.g. "IVA Descontable", nature D, living inside the
     * credit-nature Pasivos class) instead of netting it against the
     * class's normal accounts -- see getAccountBalances() for the full
     * story. Fixed by orienting each account's raw (debit-positive)
     * balance to this class's normal polarity ONCE here -- Pasivos and
     * Patrimonio are normally credit-nature classes, so their raw
     * (debit-positive) balance is negated; Activo is normally
     * debit-nature, so it's used as-is -- and summing THAT signed,
     * oriented value for the section total, while still showing each
     * line's absolute value on screen (unchanged display behavior).
     */
    private List<BalanceSheetSection> buildSectionsForClass(
            AccountClass accountClass,
            Map<String, BigDecimal> accountBalances) {

        List<Object[]> accounts = chartOfAccountsRepository.getAccountsForBalanceSheet(
                currentTenantId(),
                accountClass
        );

        boolean creditNormal = accountClass == AccountClass.LIABILITY || accountClass == AccountClass.EQUITY;

        Map<AccountCategory, List<BalanceSheetSection.AccountLine>> categorizedAccounts = new LinkedHashMap<>();
        Map<AccountCategory, BigDecimal> orientedTotals = new LinkedHashMap<>();

        for (Object[] row : accounts) {
            try {
                String accountCode = (String) row[0];
                String accountName = (String) row[1];
                AccountCategory category = (AccountCategory) row[2];
                Integer displayOrder = row[3] != null ? (Integer) row[3] : 999;

                BigDecimal rawBalance = accountBalances.getOrDefault(accountCode, BigDecimal.ZERO);
                // Orient to this class's normal direction: positive means
                // a normal balance for an account of this class, negative
                // means a contra account working against it (and must
                // subtract from the section total, not add to it).
                BigDecimal orientedBalance = creditNormal ? rawBalance.negate() : rawBalance;

                // Only include accounts with non-zero balances
                if (orientedBalance.compareTo(BigDecimal.ZERO) != 0) {
                    BalanceSheetSection.AccountLine line = BalanceSheetSection.AccountLine.builder()
                            .accountCode(accountCode)
                            .accountName(accountName)
                            .balance(orientedBalance.abs()) // Always positive in display
                            .build();

                    categorizedAccounts
                            .computeIfAbsent(category, k -> new ArrayList<>())
                            .add(line);
                    orientedTotals.merge(category, orientedBalance, BigDecimal::add);
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

            BigDecimal sectionTotal = orientedTotals.getOrDefault(category, BigDecimal.ZERO);

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
    // CALCULATION HELPERS (Polymorphic)
    // ═══════════════════════════════════════════════════════════

    private <T extends FinancialSection> BigDecimal calculateTotal(List<T> sections) {
        if (sections == null || sections.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return sections.stream()
                .map(FinancialSection::getSectionTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private boolean verifyAccountingEquation(
            BigDecimal totalAssets,
            BigDecimal totalLiabilities,
            BigDecimal totalEquity) {

        BigDecimal totalLiabilitiesAndEquity = totalLiabilities.add(totalEquity);
        BigDecimal difference = totalAssets.subtract(totalLiabilitiesAndEquity).abs();

        BigDecimal tolerance = new BigDecimal("0.01");

        return difference.compareTo(tolerance) <= 0;
    }

    // ═══════════════════════════════════════════════════════════
    // INCOME STATEMENT
    // ═══════════════════════════════════════════════════════════

    public IncomeStatementReport getIncomeStatement(LocalDate startDate, LocalDate endDate) {
        Company company = TenantContext.getCurrentCompany();
        Long companyId = currentTenantId();

        log.info("Generating Income Statement for company ID: {} from {} to {}",
                companyId, startDate, endDate);

        if (startDate == null || endDate == null) {
            throw new InvalidOperationException("Start date and end date are required for Income Statement");
        }

        if (startDate.isAfter(endDate)) {
            throw new InvalidOperationException("Start date cannot be after end date");
        }

        if (endDate.isAfter(LocalDate.now())) {
            throw new InvalidOperationException("Cannot generate Income Statement for future dates");
        }

        // STEP 1: Build Revenue Sections
        List<IncomeStatementSection> revenueSections = buildSectionsForIncomeStatement(
                AccountClass.REVENUE, startDate, endDate
        );
        BigDecimal totalRevenue = calculateTotal(revenueSections);

        // STEP 2: Build Cost Sections
        List<IncomeStatementSection> costSections = buildSectionsForIncomeStatement(
                AccountClass.COST, startDate, endDate
        );
        BigDecimal totalCosts = calculateTotal(costSections);

        // STEP 3: Calculate Gross Profit
        BigDecimal grossProfit = totalRevenue.subtract(totalCosts);

        // STEP 4: Build Expense Sections
        List<IncomeStatementSection> allExpenseSections = buildSectionsForIncomeStatement(
                AccountClass.EXPENSE, startDate, endDate
        );

        List<IncomeStatementSection> operatingExpenseSections = new ArrayList<>();
        List<IncomeStatementSection> nonOperatingExpenseSections = new ArrayList<>();
        List<IncomeStatementSection> taxExpenseSections = new ArrayList<>();

        for (IncomeStatementSection section : allExpenseSections) {
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

        // STEP 5: Calculate Key Subtotals
        BigDecimal operatingIncome = grossProfit.subtract(totalOperatingExpenses);
        BigDecimal incomeBeforeTaxes = operatingIncome.subtract(totalNonOperatingExpenses);
        BigDecimal netIncome = incomeBeforeTaxes.subtract(totalTaxExpenses);

        // STEP 6: Calculate Financial Metrics
        BigDecimal grossProfitMargin = calculateMargin(grossProfit, totalRevenue);
        BigDecimal operatingMargin = calculateMargin(operatingIncome, totalRevenue);
        BigDecimal netProfitMargin = calculateMargin(netIncome, totalRevenue);

        // STEP 7: Build and Return Report
        return IncomeStatementReport.builder()
                .companyName(company.getLegalName())
                .startDate(startDate)
                .endDate(endDate)
                .generatedAt(LocalDateTime.now())
                .revenueSections(revenueSections)
                .totalRevenue(totalRevenue.setScale(2, RoundingMode.HALF_UP))
                .costSections(costSections)
                .totalCosts(totalCosts.setScale(2, RoundingMode.HALF_UP))
                .grossProfit(grossProfit.setScale(2, RoundingMode.HALF_UP))
                .operatingExpenseSections(operatingExpenseSections)
                .totalOperatingExpenses(totalOperatingExpenses.setScale(2, RoundingMode.HALF_UP))
                .operatingIncome(operatingIncome.setScale(2, RoundingMode.HALF_UP))
                .nonOperatingExpenseSections(nonOperatingExpenseSections)
                .totalNonOperatingExpenses(totalNonOperatingExpenses.setScale(2, RoundingMode.HALF_UP))
                .incomeBeforeTaxes(incomeBeforeTaxes.setScale(2, RoundingMode.HALF_UP))
                .taxExpenseSections(taxExpenseSections)
                .totalTaxExpenses(totalTaxExpenses.setScale(2, RoundingMode.HALF_UP))
                .netIncome(netIncome.setScale(2, RoundingMode.HALF_UP))
                .grossProfitMargin(grossProfitMargin.setScale(1, RoundingMode.HALF_UP))
                .operatingMargin(operatingMargin.setScale(1, RoundingMode.HALF_UP))
                .netProfitMargin(netProfitMargin.setScale(1, RoundingMode.HALF_UP))
                .build();
    }

    /**
     * Hierarchical Income Statement (Estado de Resultados / Pérdidas y
     * Ganancias) for a date range -- full PUC rollup at every level,
     * same tree-walking approach as
     * JournalEntryService.getTrialBalanceDetailed(), but scoped to
     * Ingresos (4), Gastos (5) and Costos (6/7) and with no opening
     * balance (Income Statement accounts are temporary, they reset
     * every period).
     *
     * Built 2026-10-12 at the user's request, after they showed a
     * "PERDIDA O GANANCIA" report from their legacy system (Siewin)
     * with this exact three-block layout (Ingresos / Gastos / Costo de
     * Ventas, each with a full hierarchical subtotal) and asked for the
     * bottom line to be labeled "Utilidad antes de Impuestos" or
     * "Pérdida antes de Impuestos" depending on the sign -- see
     * IncomeStatementDetailedReport for why that label is computed here
     * instead of left for the frontend to derive from a signed number.
     *
     * Deliberately a NEW report, not an extension of the existing flat
     * getIncomeStatement()/IncomeStatementReport above: that one groups
     * by AccountCategory (Operating/Non-Operating/Tax, gross/operating
     * margins, etc.) for a different kind of analysis, has no endpoint
     * or frontend screen today, and reshaping it into a full PUC
     * hierarchy would have meant changing its tested shape for a use
     * case it was never built for. Both can coexist.
     */
    public IncomeStatementDetailedReport getIncomeStatementDetailed(LocalDate startDate, LocalDate endDate) {
        Company company = TenantContext.getCurrentCompany();
        Long companyId = currentTenantId();

        log.info("Generating detailed Income Statement for company ID: {} from {} to {}", companyId, startDate, endDate);

        if (startDate == null || endDate == null) {
            throw new InvalidOperationException("Start date and end date are required for Income Statement");
        }
        if (startDate.isAfter(endDate)) {
            throw new InvalidOperationException("Start date cannot be after end date");
        }
        if (endDate.isAfter(LocalDate.now())) {
            throw new InvalidOperationException("Cannot generate Income Statement for future dates");
        }

        // Raw period activity (debit, credit) per account code -- same
        // orientation-free shape as the query the Trial Balance
        // Detallado uses (fixed 2026-10-10), but EXCLUDING the
        // auto-generated "CIERRE" closing entry's own postings.
        //
        // BUG FIX (2026-10-12): using plain getPeriodActivity() here
        // (like the Trial Balance Detallado does) made this report come
        // back empty for any CLOSED year -- the closing entry is dated
        // December 31 of that same year, inside the report's own date
        // range, and its lines zero out every Ingresos/Gastos/Costos
        // account by design (that's how the net result moves to
        // Patrimonio). Summed together with the year's real activity,
        // every result account nets to (near) zero. The Estado de
        // Resultados has to show the period's real economic activity
        // regardless of whether the year has been closed since -- the
        // user needs to audit a closed year's P&L exactly like the
        // legacy SIEWIN system allowed, not see it wiped out by its own
        // closing entry. getPeriodActivityExcludingClosingEntries()
        // (new) is a separate query so the Trial Balance Detallado,
        // which legitimately DOES want the closing entry's effect
        // (showing the real zeroed closing balance of each result
        // account), is completely unaffected by this fix.
        Map<String, BigDecimal[]> periodActivityByCode = new HashMap<>();
        List<Object[]> activities = chartOfAccountsRepository.getPeriodActivityExcludingClosingEntries(companyId, startDate, endDate);
        for (Object[] row : activities) {
            String code = (String) row[0];
            BigDecimal periodDebit = row[4] != null ? (BigDecimal) row[4] : BigDecimal.ZERO;
            BigDecimal periodCredit = row[5] != null ? (BigDecimal) row[5] : BigDecimal.ZERO;
            periodActivityByCode.put(code, new BigDecimal[]{
                    periodDebit.setScale(2, RoundingMode.HALF_UP),
                    periodCredit.setScale(2, RoundingMode.HALF_UP)
            });
        }

        // Full active chart (not just posting accounts) -- header
        // accounts never appear in getPeriodActivity() directly, but
        // still need a row here with a rolled-up subtotal. Already
        // ordered by code, which -- since every PUC code is a literal
        // string prefix of its children's codes -- is already a valid
        // pre-order tree traversal, same as getTrialBalanceDetailed().
        List<ChartOfAccounts> allAccounts = chartOfAccountsRepository.findByCompanyIdAndActiveTrueOrderByCodeAsc(companyId);

        // Raw (debit - credit) rolled up to every ancestor. Oriented to
        // a readable sign only afterwards, per top-level class -- never
        // per account's own nature, so a contra account (e.g.
        // "Devoluciones en Ventas" under Ingresos) nets correctly
        // against its siblings instead of getting its own sign flip.
        Map<String, BigDecimal> rolledUpRaw = new HashMap<>();
        for (ChartOfAccounts account : allAccounts) {
            rolledUpRaw.put(account.getCode(), BigDecimal.ZERO);
        }
        for (ChartOfAccounts account : allAccounts) {
            if (!account.isPostingAccount()) {
                continue; // only leaves ever have direct activity to roll up
            }
            BigDecimal[] activity = periodActivityByCode.getOrDefault(
                    account.getCode(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal rawNet = activity[0].subtract(activity[1]);

            ChartOfAccounts current = account;
            while (current != null) {
                rolledUpRaw.merge(current.getCode(), rawNet, BigDecimal::add);
                current = current.getParent();
            }
        }

        List<IncomeStatementLineDetailed> revenueLines = new ArrayList<>();
        List<IncomeStatementLineDetailed> expenseLines = new ArrayList<>();
        List<IncomeStatementLineDetailed> costLines = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;
        BigDecimal totalCosts = BigDecimal.ZERO;

        for (ChartOfAccounts account : allAccounts) {
            if (!account.isIncomeStatementAccount()) {
                continue; // Balance Sheet accounts (1,2,3) never belong here
            }

            BigDecimal raw = rolledUpRaw.getOrDefault(account.getCode(), BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);

            boolean isRevenue = account.getAccountClass() == AccountClass.REVENUE;
            BigDecimal amount = isRevenue ? raw.negate() : raw;

            if (amount.compareTo(BigDecimal.ZERO) == 0) {
                continue; // nothing to show for this branch -- same
                          // "hide genuinely empty rows" rule as the
                          // Trial Balance Detallado and Cost Center /
                          // Third Party reports.
            }

            IncomeStatementLineDetailed line = IncomeStatementLineDetailed.builder()
                    .accountCode(account.getCode())
                    .accountName(account.getName())
                    .level(account.getLevel() != null ? account.getLevel().intValue() : 1)
                    .postingAccount(account.isPostingAccount())
                    .amount(amount)
                    .build();

            switch (account.getAccountClass()) {
                case REVENUE -> {
                    revenueLines.add(line);
                    if (account.isPostingAccount()) {
                        totalRevenue = totalRevenue.add(amount);
                    }
                }
                case EXPENSE -> {
                    expenseLines.add(line);
                    if (account.isPostingAccount()) {
                        totalExpenses = totalExpenses.add(amount);
                    }
                }
                case COST -> {
                    costLines.add(line);
                    if (account.isPostingAccount()) {
                        totalCosts = totalCosts.add(amount);
                    }
                }
                default -> { } // unreachable: isIncomeStatementAccount() already filtered to 4/5/6
            }
        }

        totalRevenue = totalRevenue.setScale(2, RoundingMode.HALF_UP);
        totalExpenses = totalExpenses.setScale(2, RoundingMode.HALF_UP);
        totalCosts = totalCosts.setScale(2, RoundingMode.HALF_UP);

        BigDecimal netResultBeforeTaxes = totalRevenue.subtract(totalExpenses).subtract(totalCosts)
                .setScale(2, RoundingMode.HALF_UP);
        boolean profit = netResultBeforeTaxes.compareTo(BigDecimal.ZERO) >= 0;

        return IncomeStatementDetailedReport.builder()
                .companyName(company.getLegalName())
                .startDate(startDate)
                .endDate(endDate)
                .generatedAt(LocalDateTime.now())
                .revenueLines(revenueLines)
                .totalRevenue(totalRevenue)
                .expenseLines(expenseLines)
                .totalExpenses(totalExpenses)
                .costLines(costLines)
                .totalCosts(totalCosts)
                .netResultBeforeTaxes(netResultBeforeTaxes)
                .resultLabel(profit ? "Profit Before Taxes" : "Loss Before Taxes")
                .resultLabelEs(profit ? "Utilidad antes de Impuestos" : "Pérdida antes de Impuestos")
                .build();
    }

    /**
     * BUG FIX (2026-10-10): same fix pattern as
     * FinancialStatementService.buildSectionsForClass() (Balance Sheet)
     * -- getAccountsForIncomeStatement() now returns the RAW,
     * nature-agnostic periodBalance (debit - credit) for every account,
     * and the sign is oriented ONCE here per the section's normal
     * polarity (REVENUE is normally credit-nature, COST/EXPENSE are
     * normally debit-nature), so a contra account (e.g. "Devoluciones en
     * Ventas" under Revenue, or "Descuentos en Compras" under Costs)
     * correctly SUBTRACTS from the section total instead of being added
     * to it. Each line's displayed amount is still always positive
     * (unchanged), only the section total's calculation changed.
     */
    private List<IncomeStatementSection> buildSectionsForIncomeStatement(
            AccountClass accountClass,
            LocalDate startDate,
            LocalDate endDate) {

        List<Object[]> accounts = chartOfAccountsRepository.getAccountsForIncomeStatement(
                currentTenantId(),
                accountClass,
                startDate,
                endDate
        );

        // Revenue is normally credit-nature; Cost and Expense are
        // normally debit-nature (see AccountClass).
        boolean creditNormal = accountClass == AccountClass.REVENUE;

        Map<AccountCategory, List<IncomeStatementSection.AccountLine>> categorizedAccounts = new LinkedHashMap<>();
        Map<AccountCategory, BigDecimal> orientedTotals = new LinkedHashMap<>();

        for (Object[] row : accounts) {
            try {
                String accountCode = (String) row[0];
                String accountName = (String) row[1];
                AccountCategory category = (AccountCategory) row[2];
                Integer displayOrder = row[3] != null ? (Integer) row[3] : 999;
                BigDecimal rawPeriodBalance = row[4] != null ? (BigDecimal) row[4] : BigDecimal.ZERO;
                BigDecimal orientedBalance = creditNormal ? rawPeriodBalance.negate() : rawPeriodBalance;

                if (orientedBalance.compareTo(BigDecimal.ZERO) != 0) {
                    IncomeStatementSection.AccountLine line = IncomeStatementSection.AccountLine.builder()
                            .accountCode(accountCode)
                            .accountName(accountName)
                            .amount(orientedBalance.abs())
                            .build();

                    categorizedAccounts
                            .computeIfAbsent(category, k -> new ArrayList<>())
                            .add(line);
                    orientedTotals.merge(category, orientedBalance, BigDecimal::add);
                }
            } catch (Exception e) {
                log.error("Error processing account data from row", e);
            }
        }

        List<IncomeStatementSection> sections = new ArrayList<>();

        for (Map.Entry<AccountCategory, List<IncomeStatementSection.AccountLine>> entry : categorizedAccounts.entrySet()) {
            AccountCategory category = entry.getKey();
            List<IncomeStatementSection.AccountLine> lines = entry.getValue();

            BigDecimal sectionTotal = orientedTotals.getOrDefault(category, BigDecimal.ZERO);

            IncomeStatementSection section = IncomeStatementSection.builder()
                    .sectionName(category.getDisplayName())
                    .sectionNameEs(category.getDisplayNameEs())
                    .accountLines(lines)
                    .sectionTotal(sectionTotal.setScale(2, RoundingMode.HALF_UP))
                    .displayOrder(category.getDisplayOrder())
                    .build();

            sections.add(section);
        }

        sections.sort(Comparator.comparing(IncomeStatementSection::getDisplayOrder));

        return sections;
    }

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
}