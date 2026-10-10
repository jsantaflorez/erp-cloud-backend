package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.JournalEntry;
import com.erp.erp_cloud.entity.JournalEntryItem;
import com.erp.erp_cloud.entity.ThirdParty;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    // ═══════════════════════════════════════════════════════════
    // ADVANCED SEARCH ENGINE (With Native Tenant & Pagination Shield)
    // ═══════════════════════════════════════════════════════════

    /**
     * Search journal entries with filters.
     * Excludes logically deleted records (active = false).
     * ADAPTED: Uses companyId (Long) and synchronizes countQuery filters to prevent Pageable discrepancies.
     *
     * BUG FIX (2026-09-10): documentNumber used to match only as a PREFIX
     * (CONCAT(:searchTerm, '%')) while description matched anywhere
     * (CONCAT('%', :searchTerm, '%')) -- the only inconsistent case in the
     * whole codebase (compare ThirdPartyRepository, which already does a
     * substring match on documentNumber too). A user searching for a
     * fragment in the middle of a document number (e.g. "0045" inside
     * "EG-0045") got zero results and no indication why. Both fields now
     * match anywhere, like everywhere else in the app.
     *
     * Also added: documentTypeId, so entries can be filtered by document
     * type (Recibo de Caja, Egreso, etc.), not just by number/description/date.
     */
    @Query(value = "SELECT j FROM JournalEntry j " +
            "JOIN FETCH j.documentType " +
            "WHERE j.company.id = :companyId " +
            "AND j.active = true " +
            "AND (:searchTerm IS NULL OR " +
            "     LOWER(j.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "     LOWER(j.documentNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "AND (:startDate IS NULL OR j.entryDate >= :startDate) " +
            "AND (:endDate IS NULL OR j.entryDate <= :endDate) " +
            "AND (:documentTypeId IS NULL OR j.documentType.id = :documentTypeId)",
            countQuery = "SELECT COUNT(j) FROM JournalEntry j " +
                    "WHERE j.company.id = :companyId " +
                    "AND j.active = true " +
                    "AND (:searchTerm IS NULL OR " +
                    "     LOWER(j.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                    "     LOWER(j.documentNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
                    "AND (:startDate IS NULL OR j.entryDate >= :startDate) " +
                    "AND (:endDate IS NULL OR j.entryDate <= :endDate) " +
                    "AND (:documentTypeId IS NULL OR j.documentType.id = :documentTypeId)")
    Page<JournalEntry> searchEntries(
            @Param("companyId") Long companyId,
            @Param("searchTerm") String searchTerm,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("documentTypeId") Long documentTypeId,
            Pageable pageable);

    // ═══════════════════════════════════════════════════════════
    // ADAPTED TENANT METHODS (Primitive ID-based for optimization)
    // ═══════════════════════════════════════════════════════════

    /**
     * Checks if a third party has associated transactions in active journal entries under the current tenant context.
     * ADAPTED: Multi-tenant defense added to ensure data isolation.
     */
    @Query("SELECT CASE WHEN COUNT(item) > 0 THEN true ELSE false END " +
            "FROM JournalEntry e JOIN e.items item " +
            "WHERE e.company.id = :companyId " +
            "AND item.thirdParty = :thirdParty " +
            "AND e.active = true")
    boolean existsByCompanyIdAndThirdParty(@Param("companyId") Long companyId, @Param("thirdParty") ThirdParty thirdParty);

    /**
     * Checks for duplicate document numbers.
     * ADAPTED: Uses CompanyId instead of Company entity to support multi-tenant native long IDs.
     */
    boolean existsByCompanyIdAndDocumentNumberAndActiveTrue(Long companyId, String documentNumber);

    /**
     * Distinct dates with at least one journal entry for a company
     * (startDate/endDate optional -- null means unbounded, same
     * convention as searchEntries() above). NEW (2026-10-10), added to
     * tell which months actually have activity. Backs two things in
     * AccountingPeriodService: (1) validateAllMonthsClosedBeforeYearEnd,
     * which only requires a month to be explicitly closed before
     * year-end when it has real movements -- a company whose books
     * start mid-year (first fiscal year, or migrated from a legacy
     * system) must not be forced to "close" months that never existed
     * for it; and (2) the Periodos Contables screen, which shows a
     * virtual "Abierto" row for any month with activity that was never
     * explicitly closed/reopened, so it is not invisible just because
     * no AccountingPeriod row exists yet for it. Deliberately includes
     * annulled entries -- even a reversed entry means something
     * happened that month and still deserves an explicit, reviewed
     * close, not a silent skip.
     */
    @Query("SELECT DISTINCT j.entryDate FROM JournalEntry j " +
            "WHERE j.company.id = :companyId " +
            "AND (:startDate IS NULL OR j.entryDate >= :startDate) " +
            "AND (:endDate IS NULL OR j.entryDate <= :endDate)")
    List<LocalDate> findDistinctEntryDates(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Alias for compatibility with existing Service logic.
     * ADAPTED: Accepts Long companyId and routes to the updated query method.
     */
    default boolean existsByCompanyIdAndDocumentNumber(Long companyId, String documentNumber) {
        return existsByCompanyIdAndDocumentNumberAndActiveTrue(companyId, documentNumber);
    }

    /**
     * Finds an active journal entry by document number.
     * ADAPTED: Derived query method using CompanyId.
     */
    Optional<JournalEntry> findByCompanyIdAndDocumentNumberAndActiveTrue(Long companyId, String documentNumber);

    /**
     * Alias for compatibility with existing Service logic.
     * ADAPTED: Accepts Long companyId and routes to the active-check query method.
     */
    default Optional<JournalEntry> findByCompanyIdAndDocumentNumber(Long companyId, String documentNumber) {
        return findByCompanyIdAndDocumentNumberAndActiveTrue(companyId, documentNumber);
    }

    // ═══════════════════════════════════════════════════════════
    // CORE FINANCIAL REPORTING METHODS
    // ═══════════════════════════════════════════════════════════

    /**
     * Calculates account balances for the Trial Balance report.
     * ADAPTED: Evaluates a.company.id against the primitive Long parameter.
     *
     * BUG FIX (2026-09-08): dropped "AND a.active = true". This report is
     * explicitly generated for an arbitrary past asOfDate (see
     * FinancialStatementService.getBalanceSheet), but the CURRENT active
     * flag has nothing to do with whether the account legitimately had a
     * balance as of that historical date -- deactivating an account (e.g.
     * closing an old bank account) is meant to block its use in NEW entries
     * only, per the deactivate() docs ("Historical transactions are
     * preserved... Deactivated accounts remain visible in historical
     * reports"). Filtering on current active status here made a Balance
     * Sheet for a PAST date silently drop any account deactivated since
     * then, even though it correctly had a balance on that date. Safe to
     * remove: an inactive account with no real postings simply never
     * matches the join to JournalEntryItem in the first place, so this
     * can't resurrect zero-activity dead accounts -- only genuinely
     * historical balances.
     */
    @Query("""
        SELECT 
            a.code,
            CAST(a.nature AS string),
            COALESCE(SUM(i.debit), 0),
            COALESCE(SUM(i.credit), 0)
        FROM JournalEntryItem i
        JOIN i.account a
        JOIN i.journalEntry je
        WHERE a.company.id = :companyId
          AND je.entryDate <= :asOfDate
          AND je.active = true
          AND a.postingAccount = true
        GROUP BY a.code, a.nature
        ORDER BY a.code
    """)
    List<Object[]> getAccountBalancesAsOfDate(
            @Param("companyId") Long companyId,
            @Param("asOfDate") LocalDate asOfDate
    );

    /**
     * Retrieves all items for the Auxiliary Ledger.
     * ADAPTED: Filters via e.company.id utilizing the primitive tenant ID.
     */
    @Query("""
        SELECT i FROM JournalEntry e 
        JOIN e.items i 
        JOIN i.account a 
        WHERE e.company.id = :companyId 
          AND e.active = true 
          AND e.entryDate BETWEEN :startDate AND :endDate 
          AND a.code BETWEEN :startCode AND :endCode 
          AND a.postingAccount = true 
        ORDER BY a.code ASC, e.entryDate ASC, e.id ASC
    """)
    List<JournalEntryItem> findItemsForAuxiliary(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("startCode") String startCode,
            @Param("endCode") String endCode
    );

    /**
     * Opening balance per cost center, as of a cutoff date, for the
     * "Auxiliar por Centro de Costo" report (formerly a period-only
     * "Balance por Centro de Costo" -- upgraded 2026-09-21 after the user
     * showed a real report from their previous system carrying an actual
     * S.I./Nuevo Saldo per cost center, not just period totals: a cost
     * center attached to a balance-sheet account, like inventory or
     * receivables, genuinely accumulates a balance the same way the
     * account itself does).
     *
     * IMPORTANT: unlike getOpeningBalancesForAuxiliary (grouped by account,
     * where a.nature is constant per group), one cost center can span
     * MULTIPLE accounts with DIFFERENT natures (e.g. an expense account
     * and an income account both tagged to the same cost center). The
     * CASE on a.nature must therefore be evaluated PER ROW, inside the
     * SUM -- never as one CASE wrapped around the whole aggregate the way
     * the per-account query does it, which would be wrong (and wouldn't
     * even compile as HQL, since a.nature isn't a GROUP BY key here).
     *
     * Optional account range narrows this to specific accounts (e.g. just
     * cartera or inventory), same convention as the Auxiliary Ledger's
     * startCode/endCode. Optional costCenterCode narrows to one cost
     * center; null means all of them, same as "TODOS LOS CENTROS DE
     * COSTO" in the reference report.
     */
    @Query("""
        SELECT
            cc.code,
            cc.name,
            COALESCE(SUM(
                CASE WHEN a.nature = 'D' THEN i.debit - i.credit ELSE i.credit - i.debit END
            ), 0) as openingBalance
        FROM JournalEntryItem i
        JOIN i.costCenter cc
        JOIN i.account a
        JOIN i.journalEntry je
        WHERE je.company.id = :companyId
          AND je.active = true
          AND je.entryDate < :startDate
          AND a.code BETWEEN :startCode AND :endCode
          AND cc.code = COALESCE(:costCenterCode, cc.code)
        GROUP BY cc.code, cc.name
        ORDER BY cc.code ASC
    """)
    List<Object[]> getCostCenterOpeningBalances(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDate startDate,
            @Param("startCode") String startCode,
            @Param("endCode") String endCode,
            @Param("costCenterCode") String costCenterCode
    );

    /**
     * All transaction items within a date range for the "Auxiliar por
     * Centro de Costo" report, restricted to items that actually have a
     * cost center assigned (the inner join to i.costCenter does that) and
     * optionally to one specific cost center and/or account range.
     * Ordered by cost center first so the service can group consecutive
     * rows by cost center in one pass.
     */
    @Query("""
        SELECT i FROM JournalEntry e
        JOIN e.items i
        JOIN i.costCenter cc
        JOIN i.account a
        WHERE e.company.id = :companyId
          AND e.active = true
          AND e.entryDate BETWEEN :startDate AND :endDate
          AND a.code BETWEEN :startCode AND :endCode
          AND cc.code = COALESCE(:costCenterCode, cc.code)
        ORDER BY cc.code ASC, e.entryDate ASC, e.id ASC
    """)
    List<JournalEntryItem> findItemsForCostCenterAuxiliary(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("startCode") String startCode,
            @Param("endCode") String endCode,
            @Param("costCenterCode") String costCenterCode
    );

    /**
     * Opening balance per third party, as of a cutoff date, for the
     * "Estado de Cuenta por Tercero" report -- same shape as
     * getCostCenterOpeningBalances (S.I./Nuevo Saldo instead of a plain
     * period total), grouped by third party instead of cost center.
     *
     * IMPORTANT, and DELIBERATELY DIFFERENT from the Cost Center report:
     * this is NOT nature-aware. A third party's statement of account
     * behaves like a receivable/payable subledger, not like a resource
     * flow -- the balance is always "opening balance + debits - credits"
     * regardless of which account (expense, revenue, tax, cartera...) a
     * given line actually posted to, the same way the user's reference
     * system computes it. Confirmed with the user (2026-09-25) after an
     * initial nature-aware version -- copied from the Cost Center
     * report's pattern -- produced a running balance the user correctly
     * flagged as not matching "saldo inicial + debitos - creditos".
     *
     * Selects the raw name components instead of a single "name" column
     * (unlike CostCenter, ThirdParty has no single display name field --
     * see ThirdParty.getLegalDisplayName()) so the service can rebuild
     * the exact same display name Java-side, without duplicating that
     * logic in HQL. All of them have to be in GROUP BY together with
     * tp.id, since SQL requires every selected non-aggregated column to
     * be part of the grouping.
     *
     * No cost center filter here -- see
     * getThirdPartyOpeningBalancesForCostCenter for that. Kept as two
     * separate methods instead of one with an optional
     * "(:costCenterCode IS NULL OR ...)" guard on a LEFT JOIN, after a
     * real bug (2026-09-24, Cost Center report) where reusing a
     * parameter inside an OR guard silently excluded every row when the
     * filter was left empty -- and with a LEFT JOIN'ed, genuinely
     * nullable association like costCenter, a single COALESCE isn't
     * enough either (COALESCE(:param, cc.code) still compares NULL =
     * NULL as unknown when both the filter and the item's cost center
     * are absent), so two dedicated queries are the safest fix.
     */
    @Query("""
        SELECT
            tp.id,
            tp.documentNumber,
            tp.businessName,
            tp.firstName,
            tp.middleName,
            tp.lastName,
            tp.secondLastName,
            tp.tradeName,
            COALESCE(SUM(i.debit - i.credit), 0) as openingBalance
        FROM JournalEntryItem i
        JOIN i.thirdParty tp
        JOIN i.account a
        JOIN i.journalEntry je
        WHERE je.company.id = :companyId
          AND je.active = true
          AND je.entryDate < :startDate
          AND a.code BETWEEN :startCode AND :endCode
          AND tp.documentNumber = COALESCE(:thirdPartyDocument, tp.documentNumber)
        GROUP BY tp.id, tp.documentNumber, tp.businessName, tp.firstName, tp.middleName, tp.lastName, tp.secondLastName, tp.tradeName
        ORDER BY tp.documentNumber ASC
    """)
    List<Object[]> getThirdPartyOpeningBalances(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDate startDate,
            @Param("startCode") String startCode,
            @Param("endCode") String endCode,
            @Param("thirdPartyDocument") String thirdPartyDocument
    );

    /**
     * Same as getThirdPartyOpeningBalances (including NOT being
     * nature-aware -- see that method's Javadoc), additionally
     * restricted to one specific cost center (inner join -- only called
     * when a cost center filter is actually set).
     */
    @Query("""
        SELECT
            tp.id,
            tp.documentNumber,
            tp.businessName,
            tp.firstName,
            tp.middleName,
            tp.lastName,
            tp.secondLastName,
            tp.tradeName,
            COALESCE(SUM(i.debit - i.credit), 0) as openingBalance
        FROM JournalEntryItem i
        JOIN i.thirdParty tp
        JOIN i.account a
        JOIN i.journalEntry je
        JOIN i.costCenter cc
        WHERE je.company.id = :companyId
          AND je.active = true
          AND je.entryDate < :startDate
          AND a.code BETWEEN :startCode AND :endCode
          AND tp.documentNumber = COALESCE(:thirdPartyDocument, tp.documentNumber)
          AND cc.code = :costCenterCode
        GROUP BY tp.id, tp.documentNumber, tp.businessName, tp.firstName, tp.middleName, tp.lastName, tp.secondLastName, tp.tradeName
        ORDER BY tp.documentNumber ASC
    """)
    List<Object[]> getThirdPartyOpeningBalancesForCostCenter(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDate startDate,
            @Param("startCode") String startCode,
            @Param("endCode") String endCode,
            @Param("thirdPartyDocument") String thirdPartyDocument,
            @Param("costCenterCode") String costCenterCode
    );

    /**
     * Transaction items within a date range for the "Estado de Cuenta
     * por Tercero" report, restricted to items that have a third party
     * assigned (inner join), optionally narrowed to one third party
     * and/or an account range. No cost center filter -- see
     * findItemsForThirdPartyAuxiliaryByCostCenter for that.
     */
    @Query("""
        SELECT i FROM JournalEntry e
        JOIN e.items i
        JOIN i.thirdParty tp
        JOIN i.account a
        WHERE e.company.id = :companyId
          AND e.active = true
          AND e.entryDate BETWEEN :startDate AND :endDate
          AND a.code BETWEEN :startCode AND :endCode
          AND tp.documentNumber = COALESCE(:thirdPartyDocument, tp.documentNumber)
        ORDER BY tp.documentNumber ASC, e.entryDate ASC, e.id ASC
    """)
    List<JournalEntryItem> findItemsForThirdPartyAuxiliary(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("startCode") String startCode,
            @Param("endCode") String endCode,
            @Param("thirdPartyDocument") String thirdPartyDocument
    );

    /**
     * Same as findItemsForThirdPartyAuxiliary, additionally restricted
     * to one specific cost center (inner join).
     */
    @Query("""
        SELECT i FROM JournalEntry e
        JOIN e.items i
        JOIN i.thirdParty tp
        JOIN i.account a
        JOIN i.costCenter cc
        WHERE e.company.id = :companyId
          AND e.active = true
          AND e.entryDate BETWEEN :startDate AND :endDate
          AND a.code BETWEEN :startCode AND :endCode
          AND tp.documentNumber = COALESCE(:thirdPartyDocument, tp.documentNumber)
          AND cc.code = :costCenterCode
        ORDER BY tp.documentNumber ASC, e.entryDate ASC, e.id ASC
    """)
    List<JournalEntryItem> findItemsForThirdPartyAuxiliaryByCostCenter(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("startCode") String startCode,
            @Param("endCode") String endCode,
            @Param("thirdPartyDocument") String thirdPartyDocument,
            @Param("costCenterCode") String costCenterCode
    );

    // ═══════════════════════════════════════════════════════════
    // YEAR-END CLOSING (2026-10-03)
    // ═══════════════════════════════════════════════════════════

    /**
     * Full ledger balance per account x third party x cost center, as of a
     * cutoff date (inclusive), filtered by ChartOfAccounts.closesAtYearEnd
     * -- true selects Income Statement accounts (classes 4/5/6, the ones
     * a year-end close must zero out), false selects Balance Sheet
     * accounts (1/2/3, the ones that carry a real balance forward into
     * the opening-balance snapshot of the next year). Same shape either
     * way; AccountingPeriodService.closeYear calls this twice, once per
     * case, and does something different with the numbers each time.
     *
     * No separate "annulled" filter needed: JournalEntryService.annul()
     * zeroes out an annulled entry's own item amounts in place (see its
     * Javadoc), so a plain SUM here already nets an annulled entry to
     * zero on its own -- the e.active = true filter (same convention as
     * every other query in this repository) is enough.
     *
     * Each row's third party / cost center (index 1 / 2) may come back
     * null when that line didn't carry one -- callers must group
     * accordingly rather than assume every row has both.
     */
    @Query("""
        SELECT
            a.id,
            tp.id,
            cc.id,
            COALESCE(SUM(i.debit), 0),
            COALESCE(SUM(i.credit), 0)
        FROM JournalEntry e
        JOIN e.items i
        JOIN i.account a
        LEFT JOIN i.thirdParty tp
        LEFT JOIN i.costCenter cc
        WHERE e.company.id = :companyId
          AND e.active = true
          AND a.closesAtYearEnd = :closesAtYearEnd
          AND e.entryDate <= :cutoffDate
        GROUP BY a.id, tp.id, cc.id
    """)
    List<Object[]> getYearEndBalancesByAccount(
            @Param("companyId") Long companyId,
            @Param("cutoffDate") LocalDate cutoffDate,
            @Param("closesAtYearEnd") boolean closesAtYearEnd
    );

    // ═══════════════════════════════════════════════════════════
    // LEGACY METHODS (Object-based for backward compatibility)
    // ═══════════════════════════════════════════════════════════

    @Query("SELECT CASE WHEN COUNT(item) > 0 THEN true ELSE false END " +
            "FROM JournalEntry e JOIN e.items item " +
            "WHERE item.thirdParty = :thirdParty AND e.active = true")
    boolean existsByThirdParty(@Param("thirdParty") ThirdParty thirdParty);
}