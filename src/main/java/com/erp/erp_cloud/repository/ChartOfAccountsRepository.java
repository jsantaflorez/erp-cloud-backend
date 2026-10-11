package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.dto.TrialBalanceLine;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.enums.AccountClass;
import com.erp.erp_cloud.repository.base.TenantAwareRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChartOfAccountsRepository extends TenantAwareRepository<ChartOfAccounts, Long> {

    // ═══════════════════════════════════════════════════════════
    // BASIC VALIDATIONS & HIERARCHY (Optimized with Primitive IDs)
    // ═══════════════════════════════════════════════════════════

    /**
     * Basic validations per company
     */
    Optional<ChartOfAccounts> findByCompanyIdAndCode(Long companyId, String code);

    boolean existsByCompanyIdAndCode(Long companyId, String code);

    /**
     * Chart of accounts hierarchy
     */
    // Root accounts (level 1, no parent)
    List<ChartOfAccounts> findByCompanyIdAndParentIsNullOrderByCodeAsc(Long companyId);

    // Direct children of a specific account (Updated to use Long companyId)
    List<ChartOfAccounts> findByCompanyIdAndParentIdOrderByCodeAsc(Long companyId, Long parentId);

    // ADAPTED: Checks if an account has children within the strict scope of the current company ID
    boolean existsByCompanyIdAndParentId(Long companyId, Long parentId);

    // Active accounts filtered by level with pagination (Updated to use Long companyId)
    Page<ChartOfAccounts> findByCompanyIdAndLevelAndActiveTrue(Long companyId, Byte level, Pageable pageable);

    // NEW (2026-09-29): full active chart, every level (not just posting
    // accounts), for the hierarchical "Balance de Prueba" in
    // JournalEntryService.getTrialBalanceDetailed() -- that report needs
    // header/non-posting accounts too, to roll their leaf descendants'
    // balances up into a subtotal per level, which no existing query
    // here returns (they all filter to postingAccount = true, since
    // only posting accounts ever receive journal entry items directly).
    List<ChartOfAccounts> findByCompanyIdAndActiveTrueOrderByCodeAsc(Long companyId);

    // NEW (2026-10-03): populates the Ganancia/Pérdida account dropdowns on
    // the year-end closing screen. Scoped to EQUITY (class 3) AND
    // postingAccount = true -- explicit user requirement (2026-10-03): the
    // closing entry's balancing line must land on an auxiliary/movement
    // account, never a header, same restriction already enforced
    // everywhere else a posting target is picked.
    List<ChartOfAccounts> findByCompanyIdAndAccountClassAndPostingAccountTrueAndActiveTrueOrderByCodeAsc(
            Long companyId, AccountClass accountClass);

    // ═══════════════════════════════════════════════════════════
    // LEGACY METHODS (Object-based for backward compatibility)
    // ═══════════════════════════════════════════════════════════

    boolean existsByParent(ChartOfAccounts parent);

    /**
     * Retrieves the complete catalog for a company with pagination.
     * @deprecated Use findAllByCompany(companyId, pageable) from TenantAwareRepository instead.
     */
    @Deprecated
    Page<ChartOfAccounts> findByCompanyId(Long companyId, Pageable pageable);

    // ═══════════════════════════════════════════════════════════
    // ADVANCED FUNCTIONAL QUERIES & REPORTING ENGINE
    // ═══════════════════════════════════════════════════════════

    /**
     * Retrieves accounts enabled for posting (auxiliary accounts)
     * that are active for the current company, with pagination.
     */
    @Query("SELECT c FROM ChartOfAccounts c WHERE c.company.id = :companyId " +
            "AND c.postingAccount = true " +
            "AND c.active = true")
    Page<ChartOfAccounts> findPostingAccounts(@Param("companyId") Long companyId, Pageable pageable);
    // NOTE: findPostingAccounts is a present-tense picklist (e.g. account
    // pickers in the UI) -- unlike the historical reporting queries below,
    // it's correct for it to only offer currently-active accounts, so its
    // "active = true" filter is intentionally left as-is.

    /**
     * Functional searches for UI using custom JPQL to ensure multi-tenant security.
     */
    @Query("SELECT c FROM ChartOfAccounts c WHERE c.company.id = :companyId AND " +
            "(LOWER(c.name) LIKE LOWER(CONCAT('%', :text, '%')) OR " +
            "c.code LIKE CONCAT(:text, '%'))")
    Page<ChartOfAccounts> searchByText(
            @Param("companyId") Long companyId,
            @Param("text") String text,
            Pageable pageable);

    @Query("""
        SELECT new com.erp.erp_cloud.dto.TrialBalanceLine(
            a.code, 
            a.name, 
            COALESCE(SUM(i.debit), 0), 
            COALESCE(SUM(i.credit), 0)
        )
        FROM JournalEntryItem i
        JOIN i.account a
        WHERE a.company.id = :companyId
        GROUP BY a.code, a.name
        ORDER BY a.code ASC
    """)
    List<TrialBalanceLine> getTrialBalance(@Param("companyId") Long companyId);

    /**
     * Checks if an account has any journal entry items under the current company context.
     * Used to prevent deletion/deactivation of accounts with movements.
     */
    @Query("SELECT CASE WHEN COUNT(item) > 0 THEN true ELSE false END " +
            "FROM JournalEntryItem item " +
            "WHERE item.account = :account " +
            "AND item.account.company.id = :companyId")
    boolean existsByCompanyIdAndAccount(@Param("companyId") Long companyId, @Param("account") ChartOfAccounts account);

    /**
     * Legacy integrity check.
     */
    @Query("SELECT CASE WHEN COUNT(item) > 0 THEN true ELSE false END " +
            "FROM JournalEntryItem item " +
            "WHERE item.account = :account")
    boolean existsByAccount(@Param("account") ChartOfAccounts account);

    /**
     * Gets all accounts for a specific account class for balance sheet generation.
     */
    @Query("""
        SELECT 
            a.code,
            a.name,
            a.accountCategory,
            a.displayOrder
        FROM ChartOfAccounts a
        WHERE a.company.id = :companyId
          AND a.accountClass = :accountClass
          AND a.financialStatement = 'BALANCE_SHEET'
          AND a.postingAccount = true
        ORDER BY a.displayOrder, a.code
    """)
    List<Object[]> getAccountsForBalanceSheet(
            @Param("companyId") Long companyId,
            @Param("accountClass") AccountClass accountClass
    );
    // (query above intentionally dropped "AND a.active = true" -- see the
    // BUG FIX note on getAccountBalancesAsOfDate in JournalEntryRepository
    // for why. FinancialStatementService.buildSectionsForClass() already
    // discards any account whose balance comes back zero, so this can't
    // bring back deactivated accounts that never had real activity --
    // only ones with a genuine historical balance.)

    /**
     * Generates Trial Balance as of a specific date.
     */
    @Query("""
        SELECT new com.erp.erp_cloud.dto.TrialBalanceLine(
            a.code, 
            a.name, 
            COALESCE(SUM(i.debit), 0), 
            COALESCE(SUM(i.credit), 0)
        )
        FROM JournalEntryItem i
        JOIN i.account a
        JOIN i.journalEntry je
        WHERE a.company.id = :companyId
          AND je.entryDate <= :asOfDate
          AND a.postingAccount = true
        GROUP BY a.code, a.name
        ORDER BY a.code ASC
    """)
    List<TrialBalanceLine> getTrialBalance(
            @Param("companyId") Long companyId,
            @Param("asOfDate") LocalDate asOfDate
    );

    /**
     * Gets opening balances as of a specific date.
     */
    @Query("""
        SELECT 
            a.code,
            CASE 
                WHEN a.nature = 'D' THEN COALESCE(SUM(i.debit), 0) - COALESCE(SUM(i.credit), 0)
                ELSE COALESCE(SUM(i.credit), 0) - COALESCE(SUM(i.debit), 0)
            END as openingBalance
        FROM JournalEntryItem i
        JOIN i.account a
        JOIN i.journalEntry je
        WHERE a.company.id = :companyId
          AND je.entryDate < :startDate
          AND a.postingAccount = true
          AND a.closesAtYearEnd = false
        GROUP BY a.code, a.nature
    """)
    List<Object[]> getOpeningBalances(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDate startDate
    );

    /**
     * Gets opening balances for a range of accounts as of a specific date. (For Auxiliary)
     */
    @Query("""
        SELECT 
            a.code,
            CASE 
                WHEN a.nature = 'D' THEN COALESCE(SUM(i.debit), 0) - COALESCE(SUM(i.credit), 0)
                ELSE COALESCE(SUM(i.credit), 0) - COALESCE(SUM(i.debit), 0)
            END as openingBalance
        FROM JournalEntryItem i
        JOIN i.account a
        JOIN i.journalEntry je
        WHERE a.company.id = :companyId
          AND je.entryDate < :startDate
          AND a.code BETWEEN :startAccount AND :endAccount
          AND a.postingAccount = true
        GROUP BY a.code, a.nature
        ORDER BY a.code ASC
    """)
    List<Object[]> getOpeningBalancesForAuxiliary(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDate startDate,
            @Param("startAccount") String startAccount,
            @Param("endAccount") String endAccount
    );

    /**
     * Gets period activity (debits and credits) within a date range.
     */
    @Query("""
        SELECT 
            a.code,
            a.name,
            a.accountClass,
            a.closesAtYearEnd,
            COALESCE(SUM(i.debit), 0),
            COALESCE(SUM(i.credit), 0)
        FROM JournalEntryItem i
        JOIN i.account a
        JOIN i.journalEntry je
        WHERE a.company.id = :companyId
          AND je.entryDate >= :startDate
          AND je.entryDate <= :endDate
          AND a.postingAccount = true
        GROUP BY a.code, a.name, a.accountClass, a.closesAtYearEnd
        ORDER BY a.code
    """)
    List<Object[]> getPeriodActivity(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Gets all accounts for a specific account class for Income Statement generation.
     *
     * BUG FIX (2026-10-10): periodBalance used to flip sign per the
     * account's OWN nature (D/C), same bug as the Balance Sheet's
     * getAccountBalances() -- see FinancialStatementService for the full
     * story. That broke the moment a class mixed accounts of opposite
     * nature, e.g. a contra-revenue account ("Devoluciones en Ventas",
     * nature D, under the credit-normal REVENUE class) or a contra-cost
     * account ("Descuentos en Compras", nature C, under the debit-normal
     * COST/EXPENSE classes) would come back positive and get ADDED
     * instead of subtracted. Fixed: this query now always returns the
     * RAW, nature-agnostic balance (debit - credit); the sign is
     * oriented once per section's normal polarity in
     * FinancialStatementService.buildSectionsForIncomeStatement(), not
     * per individual account.
     */
    @Query("""
        SELECT 
            a.code,
            a.name,
            a.accountCategory,
            a.displayOrder,
            COALESCE(SUM(i.debit), 0) - COALESCE(SUM(i.credit), 0) as periodBalance
        FROM JournalEntryItem i
        JOIN i.account a
        JOIN i.journalEntry je
        WHERE a.company.id = :companyId
          AND a.accountClass = :accountClass
          AND a.financialStatement = 'INCOME_STATEMENT'
          AND a.closesAtYearEnd = true
          AND a.postingAccount = true
          AND je.entryDate >= :startDate
          AND je.entryDate <= :endDate
        GROUP BY a.code, a.name, a.accountCategory, a.displayOrder
        HAVING COALESCE(SUM(i.debit), 0) - COALESCE(SUM(i.credit), 0) <> 0
        ORDER BY a.displayOrder, a.code
    """)
    List<Object[]> getAccountsForIncomeStatement(
            @Param("companyId") Long companyId,
            @Param("accountClass") AccountClass accountClass,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Alternative simplified query if the above HAVING clause causes issues.
     *
     * BUG FIX (2026-10-10): same nature-agnostic fix as
     * getAccountsForIncomeStatement() above -- kept consistent even
     * though this variant is currently unused, so it isn't a landmine
     * if it's ever wired in later.
     */
    @Query("""
        SELECT 
            a.code,
            a.name,
            a.accountCategory,
            a.displayOrder,
            COALESCE(SUM(i.debit), 0) - COALESCE(SUM(i.credit), 0) as periodBalance
        FROM JournalEntryItem i
        JOIN i.account a
        JOIN i.journalEntry je
        WHERE a.company.id = :companyId
          AND a.accountClass = :accountClass
          AND a.financialStatement = 'INCOME_STATEMENT'
          AND a.closesAtYearEnd = true
          AND a.postingAccount = true
          AND je.entryDate >= :startDate
          AND je.entryDate <= :endDate
        GROUP BY a.code, a.name, a.accountCategory, a.displayOrder
        ORDER BY a.displayOrder, a.code
    """)
    List<Object[]> getAccountsForIncomeStatementSimple(
            @Param("companyId") Long companyId,
            @Param("accountClass") AccountClass accountClass,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}