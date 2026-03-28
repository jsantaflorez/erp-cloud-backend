package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.dto.TrialBalanceLine;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.enums.AccountClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChartOfAccountsRepository extends JpaRepository<ChartOfAccounts, Long> {

    /**
     * Basic validations per company
     */
    Optional<ChartOfAccounts> findByCompanyAndCode(Company company, String code);

    boolean existsByCompanyAndCode(Company company, String code);

    /**
     * Chart of accounts hierarchy
     */
    // Root accounts (level 1, no parent)
    List<ChartOfAccounts> findByCompanyAndParentIsNullOrderByCodeAsc(Company company);

    // Direct children of a specific account
    List<ChartOfAccounts> findByCompanyAndParentIdOrderByCodeAsc(Company company, Long parentId);

    /**
     * Retrieves accounts enabled for posting (auxiliary accounts)
     * that are active for the current company, with pagination.
     */
    @Query("SELECT c FROM ChartOfAccounts c WHERE c.company = :company " +
            "AND c.postingAccount = true " +
            "AND c.active = true")
    Page<ChartOfAccounts> findPostingAccounts(@Param("company") Company company, Pageable pageable);


    /**
     * Functional searches for UI using custom JPQL to ensure multi-tenant security.
     * Updated to support Pageable.
     */
    @Query("SELECT c FROM ChartOfAccounts c WHERE c.company = :company AND " +
            "(LOWER(c.name) LIKE LOWER(CONCAT('%', :text, '%')) OR " +
            "c.code LIKE CONCAT(:text, '%'))")
    Page<ChartOfAccounts> searchByText(@Param("company") Company company, @Param("text") String text, Pageable pageable);


    // Active accounts filtered by level with pagination
    Page<ChartOfAccounts> findByCompanyAndLevelAndActiveTrue(Company company, Byte level, Pageable pageable);

    /**
     * Retrieves the complete catalog for a company with pagination.
     */
    Page<ChartOfAccounts> findByCompany(Company company, Pageable pageable);


    boolean existsByParent(ChartOfAccounts parent);




    @Query("""
    SELECT new com.erp.erp_cloud.dto.TrialBalanceLine(
        a.code, 
        a.name, 
        COALESCE(SUM(i.debit), 0), 
        COALESCE(SUM(i.credit), 0)
    )
    FROM JournalEntryItem i
    JOIN i.account a
    WHERE a.company = :company
    GROUP BY a.code, a.name
    ORDER BY a.code ASC
""")
    List<TrialBalanceLine> getTrialBalance(@Param("company") Company company);


    /**
     * Checks if an account has any journal entry items.
     * Used to prevent deletion/deactivation of accounts with movements.
     */
    @Query("SELECT CASE WHEN COUNT(item) > 0 THEN true ELSE false END " +
            "FROM JournalEntryItem item " +
            "WHERE item.account = :account")
    boolean existsByAccount(@Param("account") ChartOfAccounts account);



    /**
     * Gets all accounts for a specific account class for balance sheet generation.
     * Only includes posting accounts (auxiliaries) as they're the only ones with balances.
     *
     * Returns: [code, name, category, displayOrder]
     */
    @Query("""
        SELECT 
            a.code,
            a.name,
            a.accountCategory,
            a.displayOrder
        FROM ChartOfAccounts a
        WHERE a.company = :company
          AND a.accountClass = :accountClass
          AND a.financialStatement = 'BALANCE_SHEET'
          AND a.postingAccount = true
          AND a.active = true
        ORDER BY a.displayOrder, a.code
    """)
    List<Object[]> getAccountsForBalanceSheet(
            @Param("company") Company company,
            @Param("accountClass") AccountClass accountClass
    );

    /**
     * Generates Trial Balance as of a specific date.
     *
     * Returns all posting accounts with their debit/credit totals.
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
        WHERE a.company = :company
          AND je.entryDate <= :asOfDate
          AND a.postingAccount = true
          AND a.active = true
        GROUP BY a.code, a.name
        ORDER BY a.code ASC
    """)
    List<TrialBalanceLine> getTrialBalance(
            @Param("company") Company company,
            @Param("asOfDate") LocalDate asOfDate
    );

    /**
     * Gets opening balances as of a specific date.
     *
     * IMPORTANT: Only returns balances for Balance Sheet accounts (closesAtYearEnd = false).
     * Income Statement accounts always have opening balance = 0, so they're excluded.
     *
     * Returns: [account_code, opening_balance]
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
        WHERE a.company = :company
          AND je.entryDate < :startDate
          AND a.postingAccount = true
          AND a.active = true
          AND a.closesAtYearEnd = false
        GROUP BY a.code, a.nature
    """)
    List<Object[]> getOpeningBalances(
            @Param("company") Company company,
            @Param("startDate") LocalDate startDate
    );


    /**
     * Gets opening balances for a range of accounts as of a specific date. (For Auxiliary)
     * * Logic:
     * 1. Sums all Debits and Credits before the :startDate.
     * 2. Applies the 'Nature' rule (D: Debit - Credit | C: Credit - Debit).
     * 3. Filters by Company (Multi-tenancy).
     * 4. Filters by Account Range (Performance optimization).
     */
    @Query("""
    SELECT 
        a.code,
        /* CALCULATION: 
           If Nature is 'D' (Assets/Expenses), balance = Debits - Credits.
           If Nature is 'C' (Liabilities/Income), balance = Credits - Debits.
        */
        CASE 
            WHEN a.nature = 'D' THEN COALESCE(SUM(i.debit), 0) - COALESCE(SUM(i.credit), 0)
            ELSE COALESCE(SUM(i.credit), 0) - COALESCE(SUM(i.debit), 0)
        END as openingBalance
    FROM JournalEntryItem i
    JOIN i.account a
    JOIN i.journalEntry je
    WHERE a.company = :company         /* Ensure data isolation between clients */
      AND je.entryDate < :startDate    /* All history before the report period */
      AND a.code BETWEEN :startAccount AND :endAccount /* Range for the Aux Ledger */
      AND a.postingAccount = true      /* Only Detail/Movement accounts */
      AND a.active = true              /* Exclude deleted/disabled accounts */
    GROUP BY a.code, a.nature
    ORDER BY a.code ASC                /* Professional sorting for the report */
""")
    List<Object[]> getOpeningBalancesForAuxiliary(
            @Param("company") Company company,
            @Param("startDate") LocalDate startDate,
            @Param("startAccount") String startAccount,
            @Param("endAccount") String endAccount
    );


    /**
     * Gets period activity (debits and credits) within a date range.
     * Returns ALL posting accounts that had activity during the period.
     *
     * Returns: [code, name, accountClass, closesAtYearEnd, periodDebit, periodCredit]
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
        WHERE a.company = :company
          AND je.entryDate >= :startDate
          AND je.entryDate <= :endDate
          AND a.postingAccount = true
          AND a.active = true
        GROUP BY a.code, a.name, a.accountClass, a.closesAtYearEnd
        ORDER BY a.code
    """)
    List<Object[]> getPeriodActivity(
            @Param("company") Company company,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
// ═══════════════════════════════════════════════════════════
    // INCOME STATEMENT QUERIES
    // ═══════════════════════════════════════════════════════════

    /**
     * Gets all accounts for a specific account class for Income Statement generation.
     *
     * Returns accounts with their period balances (activity within the date range).
     * Only includes Income Statement accounts (closesAtYearEnd = true).
     * Only includes posting accounts with activity during the period.
     *
     * Account Classes for Income Statement:
     * - REVENUE (Class 4): Credit balances (shown as positive revenue)
     * - EXPENSE (Class 5): Debit balances (shown as positive expenses)
     * - COST (Class 6, 7): Debit balances (shown as positive costs)
     *
     * Returns: [code, name, accountCategory, displayOrder, periodBalance]
     *
     * Period Balance Calculation:
     * - For Revenue (Credit accounts): Total Credits - Total Debits
     * - For Expenses/Costs (Debit accounts): Total Debits - Total Credits
     */
    @Query("""
        SELECT 
            a.code,
            a.name,
            a.accountCategory,
            a.displayOrder,
            CASE 
                WHEN a.nature = 'D' THEN 
                    COALESCE(SUM(i.debit), 0) - COALESCE(SUM(i.credit), 0)
                ELSE 
                    COALESCE(SUM(i.credit), 0) - COALESCE(SUM(i.debit), 0)
            END as periodBalance
        FROM JournalEntryItem i
        JOIN i.account a
        JOIN i.journalEntry je
        WHERE a.company = :company
          AND a.accountClass = :accountClass
          AND a.financialStatement = 'INCOME_STATEMENT'
          AND a.closesAtYearEnd = true
          AND a.postingAccount = true
          AND a.active = true
          AND je.entryDate >= :startDate
          AND je.entryDate <= :endDate
        GROUP BY a.code, a.name, a.accountCategory, a.displayOrder, a.nature
        HAVING CASE 
                WHEN a.nature = 'D' THEN 
                    COALESCE(SUM(i.debit), 0) - COALESCE(SUM(i.credit), 0)
                ELSE 
                    COALESCE(SUM(i.credit), 0) - COALESCE(SUM(i.debit), 0)
               END <> 0
        ORDER BY a.displayOrder, a.code
    """)
    List<Object[]> getAccountsForIncomeStatement(
            @Param("company") Company company,
            @Param("accountClass") AccountClass accountClass,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Alternative simplified query if the above HAVING clause causes issues.
     * This version filters zero balances in the service layer instead of SQL.
     */
    @Query("""
        SELECT 
            a.code,
            a.name,
            a.accountCategory,
            a.displayOrder,
            CASE 
                WHEN a.nature = 'D' THEN 
                    COALESCE(SUM(i.debit), 0) - COALESCE(SUM(i.credit), 0)
                ELSE 
                    COALESCE(SUM(i.credit), 0) - COALESCE(SUM(i.debit), 0)
            END as periodBalance
        FROM JournalEntryItem i
        JOIN i.account a
        JOIN i.journalEntry je
        WHERE a.company = :company
          AND a.accountClass = :accountClass
          AND a.financialStatement = 'INCOME_STATEMENT'
          AND a.closesAtYearEnd = true
          AND a.postingAccount = true
          AND a.active = true
          AND je.entryDate >= :startDate
          AND je.entryDate <= :endDate
        GROUP BY a.code, a.name, a.accountCategory, a.displayOrder, a.nature
        ORDER BY a.displayOrder, a.code
    """)
    List<Object[]> getAccountsForIncomeStatementSimple(
            @Param("company") Company company,
            @Param("accountClass") AccountClass accountClass,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}


