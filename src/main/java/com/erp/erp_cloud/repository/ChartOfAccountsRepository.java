package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChartOfAccountsRepository extends JpaRepository<ChartOfAccounts, Long> {

    /**
     * Basic validations per company
     */
    Optional<ChartOfAccounts> findByCompanyAndCode(Company company, String code);

    boolean existsByCompanyAndCode(Company company, String code);

    /**
     * Chart of accounts hierarchy
     */
    // Root accounts (level 1, no parent)
    List<ChartOfAccounts> findByCompanyAndParentIsNullOrderByCodeAsc(Company company);

    // Direct children of a specific account
    List<ChartOfAccounts> findByCompanyAndParentIdOrderByCodeAsc(Company company, Long parentId);

    /**
     * Retrieves accounts enabled for posting (auxiliary accounts)
     * that are active for the current company.
     */
    @Query("SELECT c FROM ChartOfAccounts c WHERE c.company = :company " +
            "AND c.postingAccount = true " +
            "AND c.active = true " +
            "ORDER BY c.code ASC")
    List<ChartOfAccounts> findPostingAccounts(@Param("company") Company company);

    /**
     * Functional searches for UI using custom JPQL to ensure multi-tenant security
     * and better performance with ORDER BY.
     */
    @Query("SELECT c FROM ChartOfAccounts c WHERE c.company = :company AND " +
            "(LOWER(c.name) LIKE LOWER(CONCAT('%', :text, '%')) OR " +
            "LOWER(c.code) LIKE LOWER(CONCAT('%', :text, '%'))) " +
            "ORDER BY c.code ASC")
    List<ChartOfAccounts> searchByText(@Param("company") Company company, @Param("text") String text);

    // Active accounts filtered by level
    List<ChartOfAccounts> findByCompanyAndLevelAndActiveTrueOrderByCodeAsc(Company company, Byte level);

    /**
     * Retrieves the complete catalog for a company ordered by accounting code
     */
    List<ChartOfAccounts> findByCompanyOrderByCodeAsc(Company company);
}