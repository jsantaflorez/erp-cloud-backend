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

    /**
     * Finds all taxes for a specific company, ordered alphabetically by code.
     * This ensures the UI dropdowns are always consistent.
     */
    List<Tax> findByCompanyOrderByCodeAsc(Company company);

    /**
     * Finds a specific tax by its code within a company context.
     */
    Optional<Tax> findByCompanyAndCode(Company company, String code);

    /**
     * Crucial for the Accounting Engine:
     * Finds the tax rule linked to a specific account in the Chart of Accounts.
     */
    Optional<Tax> findByCompanyAndAccount(Company company, ChartOfAccounts account);

    /**
     * Used for creating/updating to prevent duplicate codes in the same company.
     */
    boolean existsByCompanyAndCode(Company company, String code);

    /**
     * Prevents assigning the same accounting account to multiple tax rules.
     */
    boolean existsByCompanyAndAccount(Company company, ChartOfAccounts account);

    /**
     * Optional: Lock a tax record during sensitive updates
     * (e.g., if you decide to track total tax collected in the future).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Tax t WHERE t.id = :id")
    Optional<Tax> findByIdWithLock(@Param("id") Long id);
}