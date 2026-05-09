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

    /**
     * Search journal entries with filters.
     * Excludes logically deleted records (active = false).
     */
    @Query(value = "SELECT j FROM JournalEntry j " +
            "JOIN FETCH j.documentType " +
            "WHERE j.company = :company " +
            "AND j.active = true " +
            "AND (:searchTerm IS NULL OR " +
            "     LOWER(j.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "     LOWER(j.documentNumber) LIKE LOWER(CONCAT(:searchTerm, '%'))) " +
            "AND (:startDate IS NULL OR j.entryDate >= :startDate) " +
            "AND (:endDate IS NULL OR j.entryDate <= :endDate)",
            countQuery = "SELECT COUNT(j) FROM JournalEntry j WHERE j.company = :company " +
                    "AND j.active = true " +
                    "AND (:searchTerm IS NULL OR LOWER(j.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<JournalEntry> searchEntries(
            @Param("company") Company company,
            @Param("searchTerm") String searchTerm,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    /**
     * Checks if a third party has associated transactions in active journal entries.
     */
    @Query("SELECT CASE WHEN COUNT(item) > 0 THEN true ELSE false END " +
            "FROM JournalEntry e JOIN e.items item " +
            "WHERE item.thirdParty = :thirdParty AND e.active = true")
    boolean existsByThirdParty(@Param("thirdParty") ThirdParty thirdParty);

    /**
     * Checks for duplicate document numbers.
     * We use 'AndActiveTrue' to allow reuse of document numbers if the previous
     * entry was logically deleted (active = false).
     */
    boolean existsByCompanyAndDocumentNumberAndActiveTrue(Company company, String documentNumber);

    /**
     * Alias for compatibility with existing Service logic.
     * Internally calls the ActiveTrue version to maintain data integrity.
     */
    default boolean existsByCompanyAndDocumentNumber(Company company, String documentNumber) {
        return existsByCompanyAndDocumentNumberAndActiveTrue(company, documentNumber);
    }

    /**
     * Finds an active journal entry by document number.
     */
    Optional<JournalEntry> findByCompanyAndDocumentNumberAndActiveTrue(Company company, String documentNumber);

    /**
     * Alias for compatibility with existing Service logic.
     */
    default Optional<JournalEntry> findByCompanyAndDocumentNumber(Company company, String documentNumber) {
        return findByCompanyAndDocumentNumberAndActiveTrue(company, documentNumber);
    }

    /**
     * Calculates account balances for the Trial Balance report.
     * Filtering by active = true ensures deleted records don't affect totals.
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
        WHERE a.company = :company
          AND je.entryDate <= :asOfDate
          AND je.active = true
          AND a.postingAccount = true
          AND a.active = true
        GROUP BY a.code, a.nature
        ORDER BY a.code
    """)
    List<Object[]> getAccountBalancesAsOfDate(
            @Param("company") Company company,
            @Param("asOfDate") LocalDate asOfDate
    );

    /**
     * Retrieves all items for the Auxiliary Ledger.
     * Includes annulled entries but excludes logically deleted entries.
     */
    @Query("""
        SELECT i FROM JournalEntry e 
        JOIN e.items i 
        JOIN i.account a 
        WHERE e.company = :company 
          AND e.active = true 
          AND e.entryDate BETWEEN :startDate AND :endDate 
          AND a.code BETWEEN :startCode AND :endCode 
          AND a.postingAccount = true 
        ORDER BY a.code ASC, e.entryDate ASC, e.id ASC
    """)
    List<JournalEntryItem> findItemsForAuxiliary(
            @Param("company") Company company,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("startCode") String startCode,
            @Param("endCode") String endCode
    );
}