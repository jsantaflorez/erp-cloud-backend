package com.erp.erp_cloud.repository;



import com.erp.erp_cloud.entity.Tax;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxRepository extends JpaRepository<Tax, Long> {

    // Finds all taxes for a specific company
    List<Tax> findByCompany(Company company);

    // Finds a tax by its unique code within a company
    Optional<Tax> findByCompanyAndCode(Company company, String code);

    // Crucial for the accounting engine: find the rule linked to an account
    Optional<Tax> findByCompanyAndAccount(Company company, ChartOfAccounts account);

    // Validation helpers
    boolean existsByCompanyAndCode(Company company, String code);
    boolean existsByCompanyAndAccount(Company company, ChartOfAccounts account);
}