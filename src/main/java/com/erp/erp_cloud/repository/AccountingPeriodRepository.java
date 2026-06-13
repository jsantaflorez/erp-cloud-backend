package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.AccountingPeriod;
import com.erp.erp_cloud.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, Long> {

    // ═══════════════════════════════════════════════════════════
    // LEGACY METHODS (Object-based for backward compatibility)
    // ═══════════════════════════════════════════════════════════

    /**
     * Finds a specific period by company, year, and month.
     */
    Optional<AccountingPeriod> findByCompanyAndYearAndMonth(Company company, Integer year, Integer month);

    /**
     * Retrieves all periods for a company, ordered by date descending.
     * Used for the main list in the Controller.
     */
    List<AccountingPeriod> findByCompanyOrderByYearDescMonthDesc(Company company);

    /**
     * Retrieves only closed periods for a company.
     */
    List<AccountingPeriod> findByCompanyAndOpenFalse(Company company);

    /**
     * Retrieves only open periods for a company.
     */
    List<AccountingPeriod> findByCompanyAndOpenTrue(Company company);

    /**
     * CRITICAL: Checks if a Fiscal Year is locked (Annual Close).
     * If true, it overrides any individual month status.
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END " +
            "FROM AccountingPeriod p " +
            "WHERE p.company = :company " +
            "AND p.year = :year " +
            "AND p.yearClose = true")
    boolean existsByCompanyAndYearAndYearCloseTrue(@Param("company") Company company, @Param("year") Integer year);

    /**
     * Checks if a period exists for a specific month.
     */
    boolean existsByCompanyAndYearAndMonth(Company company, Integer year, Integer month);

    /**
     * Retrieves all period records for a specific year.
     * Useful for bulk operations like unsealing a fiscal year.
     */
    List<AccountingPeriod> findByCompanyAndYear(Company company, Integer year);

    // ═══════════════════════════════════════════════════════════
    // ADAPTED TENANT METHODS (Primitive ID-based for optimization)
    // ═══════════════════════════════════════════════════════════

    /**
     * ADAPTED: Finds a specific period by company primitive ID, year, and month.
     */
    Optional<AccountingPeriod> findByCompanyIdAndYearAndMonth(Long companyId, Integer year, Integer month);

    /**
     * ADAPTED CRITICAL: Checks if a Fiscal Year is locked (Annual Close) using the company primitive ID.
     * Navigates directly through the foreign key parameter to avoid overhead.
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END " +
            "FROM AccountingPeriod p " +
            "WHERE p.company.id = :companyId " +
            "AND p.year = :year " +
            "AND p.yearClose = true")
    boolean existsByCompanyIdAndYearAndYearCloseTrue(@Param("companyId") Long companyId, @Param("year") Integer year);
}