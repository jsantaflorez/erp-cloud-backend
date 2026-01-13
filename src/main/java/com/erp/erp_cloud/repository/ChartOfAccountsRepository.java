package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChartOfAccountsRepository extends JpaRepository<ChartOfAccounts, Long> {

    /**
     * Basic validations per company
     */
    Optional<ChartOfAccounts> findByCompanyAndCode( Company company,String code);

    boolean existsByCompanyAndCode(Company company, String code );

    /**
     * Chart of accounts hierarchy
     */
    // Root accounts (level 1, no parent)
    List<ChartOfAccounts> findByCompanyAndParentIsNullOrderByCodeAsc(
            Company company
    );


    // Direct children of a specific account
    List<ChartOfAccounts> findByCompanyAndParentIdOrderByCodeAsc(Company company, Long parentId);

    /**
     * Accounting journal entry usage
     */
    // Active accounts allowed for posting movements
    List<ChartOfAccounts> findByCompanyAndPostingAccountTrueAndActiveTrueOrderByCodeAsc(Company company);


    /**
     * Functional searches for UI
     */
    // Autocomplete by code or name
    List<ChartOfAccounts> findByCompanyAndNameContainingIgnoreCaseOrCompanyAndCodeContainingIgnoreCase(
            Company company1, String name, Company company2, String code
    );

    // Active accounts filtered by level
    List<ChartOfAccounts> findByCompanyAndLevelAndActiveTrueOrderByCodeAsc(Company company, Byte level);


    /**
     * Retrieves the complete catalog for a company ordered by accounting code
     */
    List<ChartOfAccounts> findByCompanyOrderByCodeAsc(Company company);
}
