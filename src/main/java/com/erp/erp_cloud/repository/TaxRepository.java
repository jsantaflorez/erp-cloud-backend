package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.Tax;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxRepository extends JpaRepository<Tax, Long> {

    // ═══════════════════════════════════════════════════════════
    // ADAPTED TENANT METHODS (Primitive ID-based for optimization)
    // ═══════════════════════════════════════════════════════════

    /**
     * Finds all taxes for a specific company ID, ordered alphabetically by code.
     */
    List<Tax> findByCompanyIdOrderByCodeAsc(Long companyId);

    /**
     * Finds a specific tax by its code within a company context ID.
     */
    Optional<Tax> findByCompanyIdAndCode(Long companyId, String code);

    /**
     * Crucial for the Accounting Engine:
     * Finds the tax rule linked to a specific account and company ID.
     */
    Optional<Tax> findByCompanyIdAndAccount(Long companyId, ChartOfAccounts account);

    /**
     * Used for creating/updating to prevent duplicate codes in the same company ID.
     */
    boolean existsByCompanyIdAndCode(Long companyId, String code);

    /**
     * Prevents assigning the same accounting account to multiple tax rules within a company ID.
     */
    boolean existsByCompanyIdAndAccount(Long companyId, ChartOfAccounts account);

    /**
     * Optional: Lock a tax record during sensitive updates forcing tenant isolation.
     * ADAPTED: Enforces tenant validation during locking to prevent cross-tenant access.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Tax t WHERE t.id = :id AND t.company.id = :companyId")
    Optional<Tax> findByIdWithLockAndCompanyId(@Param("id") Long id, @Param("companyId") Long companyId);

    // ═══════════════════════════════════════════════════════════
    // LEGACY METHODS (Object-based for backward compatibility)
    // ═══════════════════════════════════════════════════════════

    List<Tax> findByCompanyOrderByCodeAsc(Company company);
    Optional<Tax> findByCompanyAndCode(Company company, String code);
    Optional<Tax> findByCompanyAndAccount(Company company, ChartOfAccounts account);
    boolean existsByCompanyAndCode(Company company, String code);
    boolean existsByCompanyAndAccount(Company company, ChartOfAccounts account);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Tax t WHERE t.id = :id")
    Optional<Tax> findByIdWithLock(@Param("id") Long id);
}