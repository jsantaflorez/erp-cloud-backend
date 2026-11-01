package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.ThirdParty;
import com.erp.erp_cloud.enums.IdentificationDocumentType;
import com.erp.erp_cloud.enums.TaxRegime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ThirdPartyRepository extends JpaRepository<ThirdParty, Long> {

    Optional<ThirdParty> findByCompanyIdAndDocumentNumber(Long companyId, String documentNumber);

    boolean existsByCompanyIdAndDocumentNumber(Long companyId, String documentNumber);

    Page<ThirdParty> findByCompanyId(Long companyId, Pageable pageable);

    /**
     * Search across all relevant fields under a specific tenant.
     * Uses COALESCE to safely prevent NULL evaluation issues in JPQL string functions.
     */
    @Query(value = "SELECT t FROM ThirdParty t WHERE t.company.id = :companyId AND (" +
            ":searchTerm IS NULL OR :searchTerm = '' OR (" +
            "UPPER(COALESCE(t.businessName, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(COALESCE(t.tradeName, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(COALESCE(t.firstName, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(COALESCE(t.middleName, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(COALESCE(t.lastName, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(COALESCE(t.secondLastName, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(COALESCE(t.email, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(COALESCE(t.billingEmail, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "t.documentNumber LIKE CONCAT('%', :searchTerm, '%')))",
            countQuery = "SELECT COUNT(t) FROM ThirdParty t WHERE t.company.id = :companyId AND (" +
                    ":searchTerm IS NULL OR :searchTerm = '' OR (" +
                    "UPPER(COALESCE(t.businessName, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
                    "UPPER(COALESCE(t.tradeName, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
                    "UPPER(COALESCE(t.firstName, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
                    "UPPER(COALESCE(t.middleName, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
                    "UPPER(COALESCE(t.lastName, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
                    "UPPER(COALESCE(t.secondLastName, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
                    "UPPER(COALESCE(t.email, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
                    "UPPER(COALESCE(t.billingEmail, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
                    "t.documentNumber LIKE CONCAT('%', :searchTerm, '%')))")
    Page<ThirdParty> findBySearchTerm(
            @Param("companyId") Long companyId,
            @Param("searchTerm") String searchTerm,
            Pageable pageable);

    // NEW (2026-11-01, user request): backs the quick filters in
    // ThirdPartyPage.jsx (Tipo de Persona/Tipo de Documento/Regimen).
    // Combines the existing search-across-name-fields OR-logic with
    // three optional equality filters, same "(:param IS NULL OR ...)"
    // pattern as the search clause above -- a null filter is a no-op,
    // so this single query also covers the old "just search, no
    // filters" case (callers pass all three filter params as null).
    //
    // FIXES a real bug reported by the user: the frontend used to
    // re-filter client-side over whatever 10 rows were already loaded
    // for the current page -- filtering by NIT showed nothing whenever
    // the loaded page's rows all happened to be CC, even though NIT
    // matches existed on other pages. This runs server-side across the
    // whole table instead.
    @Query(value = "SELECT t FROM ThirdParty t WHERE t.company.id = :companyId AND (" +
            ":searchTerm IS NULL OR :searchTerm = '' OR (" +
            "UPPER(COALESCE(t.businessName, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(COALESCE(t.tradeName, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(COALESCE(t.firstName, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(COALESCE(t.middleName, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(COALESCE(t.lastName, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(COALESCE(t.secondLastName, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(COALESCE(t.email, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(COALESCE(t.billingEmail, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "t.documentNumber LIKE CONCAT('%', :searchTerm, '%'))) AND " +
            "(:personType IS NULL OR t.personType = :personType) AND " +
            "(:documentType IS NULL OR t.documentType = :documentType) AND " +
            "(:taxRegime IS NULL OR t.taxRegime = :taxRegime)",
            countQuery = "SELECT COUNT(t) FROM ThirdParty t WHERE t.company.id = :companyId AND (" +
                    ":searchTerm IS NULL OR :searchTerm = '' OR (" +
                    "UPPER(COALESCE(t.businessName, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
                    "UPPER(COALESCE(t.tradeName, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
                    "UPPER(COALESCE(t.firstName, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
                    "UPPER(COALESCE(t.middleName, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
                    "UPPER(COALESCE(t.lastName, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
                    "UPPER(COALESCE(t.secondLastName, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
                    "UPPER(COALESCE(t.email, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
                    "UPPER(COALESCE(t.billingEmail, '')) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
                    "t.documentNumber LIKE CONCAT('%', :searchTerm, '%'))) AND " +
                    "(:personType IS NULL OR t.personType = :personType) AND " +
                    "(:documentType IS NULL OR t.documentType = :documentType) AND " +
                    "(:taxRegime IS NULL OR t.taxRegime = :taxRegime)")
    Page<ThirdParty> findByFilters(
            @Param("companyId") Long companyId,
            @Param("searchTerm") String searchTerm,
            @Param("personType") String personType,
            @Param("documentType") IdentificationDocumentType documentType,
            @Param("taxRegime") TaxRegime taxRegime,
            Pageable pageable);

    @Query("SELECT tp FROM ThirdParty tp WHERE tp.company.id = :companyId AND (" +
            "UPPER(COALESCE(tp.businessName, '')) = UPPER(:name) OR " +
            "UPPER(CONCAT(COALESCE(tp.firstName, ''), ' ', COALESCE(tp.lastName, ''))) = UPPER(:name))")
    Optional<ThirdParty> findByCompanyIdAndLegalName(@Param("companyId") Long companyId,
                                                     @Param("name") String name);

    @Query("SELECT CASE WHEN COUNT(item) > 0 THEN true ELSE false END " +
            "FROM JournalEntry e JOIN e.items item " +
            "WHERE e.company.id = :companyId " +
            "AND item.thirdParty = :thirdParty")
    boolean existsByCompanyIdAndThirdParty(@Param("companyId") Long companyId, @Param("thirdParty") ThirdParty thirdParty);
    // ═══════════════════════════════════════════════════════════
    // LEGACY METHODS (Object-based for backward compatibility)
    // ═══════════════════════════════════════════════════════════

    Optional<ThirdParty> findByCompanyAndDocumentNumber(Company company, String documentNumber);

    boolean existsByCompanyAndDocumentNumber(Company company, String documentNumber);

    Page<ThirdParty> findByCompany(Company company, Pageable pageable);

    @Query("SELECT t FROM ThirdParty t WHERE t.company = :company AND (" +
            "UPPER(t.businessName) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(t.tradeName) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(t.firstName) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(t.middleName) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(t.lastName) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(t.secondLastName) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(t.email) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(t.billingEmail) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "t.documentNumber LIKE CONCAT('%', :searchTerm, '%'))")
    Page<ThirdParty> findBySearchTerm(
            @Param("company") Company company,
            @Param("searchTerm") String searchTerm,
            Pageable pageable);

    @Query("SELECT tp FROM ThirdParty tp WHERE tp.company = :company AND (" +
            "UPPER(tp.businessName) = UPPER(:name) OR " +
            "UPPER(CONCAT(tp.firstName, ' ', tp.lastName)) = UPPER(:name))")
    Optional<ThirdParty> findByCompanyAndLegalName(@Param("company") Company company, @Param("name") String name);

    @Query("SELECT CASE WHEN COUNT(item) > 0 THEN true ELSE false END " +
            "FROM JournalEntry e JOIN e.items item " +
            "WHERE item.thirdParty = :thirdParty")
    boolean existsByThirdParty(@Param("thirdParty") ThirdParty thirdParty);
}