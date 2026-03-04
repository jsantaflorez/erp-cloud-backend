package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.JournalEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
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

    // In JournalEntryRepository
    boolean existsByCompanyAndDocumentNumber(Company company, String documentNumber);
    Optional<JournalEntry> findByCompanyAndDocumentNumber(Company company, String documentNumber);
}