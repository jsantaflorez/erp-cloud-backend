package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.AccountingPeriod;
import com.erp.erp_cloud.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, Long> {

    // ═══════════════════════════════════════════════════════════
    // ADAPTED TENANT METHODS (Primitive ID-based for optimization)
    // ═══════════════════════════════════════════════════════════

    /**
     * ADAPTED: Finds a specific period by company primitive ID, year, and month.
     */
    Optional<AccountingPeriod> findByCompanyIdAndYearAndMonth(Long companyId, Integer year, Integer month);

    /**
     * ADAPTED: Retrieves all periods for a company ID, ordered by date descending.
     */
    List<AccountingPeriod> findByCompanyIdOrderByYearDescMonthDesc(Long companyId);

    /**
     * ADAPTED: Retrieves only closed periods for a company ID.
     */
    List<AccountingPeriod> findByCompanyIdAndOpenFalse(Long companyId);

    /**
     * ADAPTED: Retrieves only open periods for a company ID.
     */
    List<AccountingPeriod> findByCompanyIdAndOpenTrue(Long companyId);

    /**
     * ADAPTED: Checks if a period exists for a specific month using company ID.
     */
    boolean existsByCompanyIdAndYearAndMonth(Long companyId, Integer year, Integer month);

    /**
     * ADAPTED: Retrieves all period records for a specific year and company ID.
     */
    List<AccountingPeriod> findByCompanyIdAndYear(Long companyId, Integer year);

    /**
     * ADAPTED CRITICAL: Checks if a Fiscal Year is locked (Annual Close) using the company primitive ID.
     * Optimized via derived query method to leverage native 'LIMIT 1' instead of full COUNT aggregation.
     */
    boolean existsByCompanyIdAndYearAndYearCloseTrue(Long companyId, Integer year);

    // ═══════════════════════════════════════════════════════════
    // LEGACY METHODS (Object-based for backward compatibility)
    // ═══════════════════════════════════════════════════════════

    Optional<AccountingPeriod> findByCompanyAndYearAndMonth(Company company, Integer year, Integer month);

    List<AccountingPeriod> findByCompanyOrderByYearDescMonthDesc(Company company);

    List<AccountingPeriod> findByCompanyAndOpenFalse(Company company);

    List<AccountingPeriod> findByCompanyAndOpenTrue(Company company);

    boolean existsByCompanyAndYearAndMonth(Company company, Integer year, Integer month);

    List<AccountingPeriod> findByCompanyAndYear(Company company, Integer year);

    boolean existsByCompanyAndYearAndYearCloseTrue(Company company, Integer year);

    /**
     * Alias for compatibility with existing service logic during migration phase.
     */
    default boolean existsByCompanyAndYearAndYearClose(Company company, Integer year) {
        return company != null && existsByCompanyIdAndYearAndYearCloseTrue(company.getId(), year);
    }
}