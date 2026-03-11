package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.dto.TrialBalanceLine;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
     * that are active for the current company, with pagination.
     */
    @Query("SELECT c FROM ChartOfAccounts c WHERE c.company = :company " +
            "AND c.postingAccount = true " +
            "AND c.active = true")
    Page<ChartOfAccounts> findPostingAccounts(@Param("company") Company company, Pageable pageable);


    /**
     * Functional searches for UI using custom JPQL to ensure multi-tenant security.
     * Updated to support Pageable.
     */
    @Query("SELECT c FROM ChartOfAccounts c WHERE c.company = :company AND " +
            "(LOWER(c.name) LIKE LOWER(CONCAT('%', :text, '%')) OR " +
            "c.code LIKE CONCAT(:text, '%'))")
    Page<ChartOfAccounts> searchByText(@Param("company") Company company, @Param("text") String text, Pageable pageable);


    // Active accounts filtered by level with pagination
    Page<ChartOfAccounts> findByCompanyAndLevelAndActiveTrue(Company company, Byte level, Pageable pageable);

    /**
     * Retrieves the complete catalog for a company with pagination.
     */
    Page<ChartOfAccounts> findByCompany(Company company, Pageable pageable);


    boolean existsByParent(ChartOfAccounts parent);




    @Query("""
    SELECT new com.erp.erp_cloud.dto.TrialBalanceLine(
        a.code, 
        a.name, 
        COALESCE(SUM(i.debit), 0), 
        COALESCE(SUM(i.credit), 0)
    )
    FROM JournalEntryItem i
    JOIN i.account a
    WHERE a.company = :company
    GROUP BY a.code, a.name
    ORDER BY a.code ASC
""")
    List<TrialBalanceLine> getTrialBalance(@Param("company") Company company);


    /**
     * Checks if an account has any journal entry items.
     * Used to prevent deletion/deactivation of accounts with movements.
     */
    @Query("SELECT CASE WHEN COUNT(item) > 0 THEN true ELSE false END " +
            "FROM JournalEntryItem item " +
            "WHERE item.account = :account")
    boolean existsByAccount(@Param("account") ChartOfAccounts account);


}