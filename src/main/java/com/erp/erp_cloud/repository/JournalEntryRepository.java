package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.JournalEntry;
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

    @Query(value = "SELECT j FROM JournalEntry j " +
            "JOIN FETCH j.documentType " + // Optimization: Fetch docType in 1 query
            "WHERE j.company = :company " +
            "AND (:searchTerm IS NULL OR " +
            "     LOWER(j.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "     LOWER(j.documentNumber) LIKE LOWER(CONCAT(:searchTerm, '%'))) " + // Faster prefix search
            "AND (:startDate IS NULL OR j.entryDate >= :startDate) " +
            "AND (:endDate IS NULL OR j.entryDate <= :endDate)",
            countQuery = "SELECT COUNT(j) FROM JournalEntry j WHERE j.company = :company " +
                    "AND (:searchTerm IS NULL OR LOWER(j.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<JournalEntry> searchEntries(
            @Param("company") Company company,
            @Param("searchTerm") String searchTerm,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);



    @Query("SELECT CASE WHEN COUNT(item) > 0 THEN true ELSE false END " +
            "FROM JournalEntry e JOIN e.items item " +
            "WHERE item.thirdParty = :thirdParty")
    boolean existsByThirdParty(@Param("thirdParty") ThirdParty thirdParty);

    boolean existsByCompanyAndDocumentNumber(Company company, String documentNumber);
    Optional<JournalEntry> findByCompanyAndDocumentNumber(Company company, String documentNumber);


    /**
     * Calculates account balances as of a specific date.
     *
     * Returns: [account_code, account_nature, total_debit, total_credit]
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
          AND a.postingAccount = true
          AND a.active = true
        GROUP BY a.code, a.nature
        ORDER BY a.code
    """)
    List<Object[]> getAccountBalancesAsOfDate(
            @Param("company") Company company,
            @Param("asOfDate") LocalDate asOfDate
    );

}