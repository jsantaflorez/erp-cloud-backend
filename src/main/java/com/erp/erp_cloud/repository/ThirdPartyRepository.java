package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.ThirdParty;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

@Repository
public interface ThirdPartyRepository extends JpaRepository<ThirdParty, Long> {

    // ═══════════════════════════════════════════════════════════
    // ADAPTED TENANT METHODS (Primitive ID-based for optimization)
    // ═══════════════════════════════════════════════════════════

    Optional<ThirdParty> findByCompanyIdAndDocumentNumber(Long companyId, String documentNumber);

    boolean existsByCompanyIdAndDocumentNumber(Long companyId, String documentNumber);

    Page<ThirdParty> findByCompanyId(Long companyId, Pageable pageable);

    /**
     * Professional search that covers all identification and name fields.
     * Aligned with the uppercase normalization in Service using primitive ID.
     * FIXED: Explicit countQuery synchronized to prevent pagination discrepancies.
     */
    @Query(value = "SELECT t FROM ThirdParty t WHERE t.company.id = :companyId AND (" +
            "UPPER(t.businessName) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(t.tradeName) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(t.firstName) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(t.middleName) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(t.lastName) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(t.secondLastName) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(t.email) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "UPPER(t.billingEmail) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
            "t.documentNumber LIKE CONCAT('%', :searchTerm, '%'))",
            countQuery = "SELECT COUNT(t) FROM ThirdParty t WHERE t.company.id = :companyId AND (" +
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
            @Param("companyId") Long companyId,
            @Param("searchTerm") String searchTerm,
            Pageable pageable);

    /**
     * Exact legal name search for internal validations or specific lookups using primitive ID
     */
    @Query("SELECT tp FROM ThirdParty tp WHERE tp.company.id = :companyId AND (" +
            "UPPER(tp.businessName) = UPPER(:name) OR " +
            "UPPER(CONCAT(tp.firstName, ' ', tp.lastName)) = UPPER(:name))")
    Optional<ThirdParty> findByCompanyIdAndLegalName(@Param("companyId") Long companyId,
                                                     @Param("name") String name);

    /**
     * ADAPTED INTEGRITY CHECK: Validates active accounting movements strictly under the current tenant context.
     * Aligned with the native multi-tenant optimization strategy.
     */
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

    Optional<ThirdParty> findByCompanyAndLegalName(Company company, String name);

    @Query("SELECT CASE WHEN COUNT(item) > 0 THEN true ELSE false END " +
            "FROM JournalEntry e JOIN e.items item " +
            "WHERE item.thirdParty = :thirdParty")
    boolean existsByThirdParty(@Param("thirdParty") ThirdParty thirdParty);
}