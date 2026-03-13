package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.AccountingPeriod;
import com.erp.erp_cloud.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, Long> {

    /**
     * Finds a specific accounting period for a company.
     */
    Optional<AccountingPeriod> findByCompanyAndYearAndMonth(Company company, Integer year, Integer month);

    /**
     * Gets all periods for a company, ordered by most recent first.
     */
    List<AccountingPeriod> findByCompanyOrderByYearDescMonthDesc(Company company);

    /**
     * Gets all closed periods for a company.
     */
    List<AccountingPeriod> findByCompanyAndIsOpenFalseOrderByYearDescMonthDesc(Company company);

    /**
     * Gets all open periods for a company.
     */
    List<AccountingPeriod> findByCompanyAndIsOpenTrueOrderByYearDescMonthDesc(Company company);

    /**
     * Checks if a period exists for a company.
     */
    boolean existsByCompanyAndYearAndMonth(Company company, Integer year, Integer month);
}