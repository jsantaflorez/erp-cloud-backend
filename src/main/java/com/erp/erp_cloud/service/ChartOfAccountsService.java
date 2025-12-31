package com.erp.erp_cloud.service;

import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.security.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ChartOfAccountsService {

    private final ChartOfAccountsRepository repository;
    private final CompanyContext companyContext;

    // =====================================================
    // CREATE
    // =====================================================
    public ChartOfAccounts create(ChartOfAccounts account) {

        Company company = companyContext.getCurrentCompany();

        boolean exists = repository.existsByCompanyAndCode(
                company,
                account.getCode()
        );

        if (exists) {
            throw new IllegalStateException(
                    "Account code already exists for this company"
            );
        }

        account.setId(null);
        account.setCompany(company);
        account.setActive(true);

        return repository.save(account);
    }

    // =====================================================
    // READ
    // =====================================================
    @Transactional(readOnly = true)
    public ChartOfAccounts findByCode(String code) {

        Company company = companyContext.getCurrentCompany();

        return repository
                .findByCompanyAndCode(company, code)
                .orElseThrow(() ->
                        new IllegalStateException("Account not found"));
    }

    @Transactional(readOnly = true)
    public ChartOfAccounts findById(Long id) {

        Company company = companyContext.getCurrentCompany();

        return repository.findById(id)
                .filter(acc -> acc.getCompany().equals(company))
                .orElseThrow(() ->
                        new IllegalStateException("Account not found"));
    }

    @Transactional(readOnly = true)
    public List<ChartOfAccounts> listRoots() {

        Company company = companyContext.getCurrentCompany();

        return repository.findByCompanyAndParentIsNullOrderByCodeAsc(company);
    }

    @Transactional(readOnly = true)
    public List<ChartOfAccounts> listChildren(Long parentId) {

        Company company = companyContext.getCurrentCompany();

        return repository.findByCompanyAndParentIdOrderByCodeAsc(
                company,
                parentId
        );
    }

    @Transactional(readOnly = true)
    public List<ChartOfAccounts> listPostingAccounts() {

        Company company = companyContext.getCurrentCompany();

        return repository
                .findByCompanyAndPostingAccountTrueAndActiveTrueOrderByCodeAsc(
                        company
                );
    }

    @Transactional(readOnly = true)
    public List<ChartOfAccounts> search(String text) {

        Company company = companyContext.getCurrentCompany();

        return repository
                .findByCompanyAndNameContainingIgnoreCaseOrCompanyAndCodeContainingIgnoreCase(
                        company,
                        text,
                        company,
                        text
                );
    }

    @Transactional(readOnly = true)
    public List<ChartOfAccounts> listByLevel(Byte level) {

        Company company = companyContext.getCurrentCompany();

        return repository
                .findByCompanyAndLevelAndActiveTrueOrderByCodeAsc(
                        company,
                        level
                );
    }

    // =====================================================
    // UPDATE
    // =====================================================
    public ChartOfAccounts update(Long id, ChartOfAccounts data) {

        ChartOfAccounts existing = findById(id);

        existing.setName(data.getName());
        existing.setNature(data.getNature());
        existing.setAccountClass(data.getAccountClass());
        existing.setAccountType(data.getAccountType());
        existing.setPostingAccount(data.getPostingAccount());

        existing.setRequiresThirdParty(data.getRequiresThirdParty());
        existing.setRequiresCostCenter(data.getRequiresCostCenter());
        existing.setRequiresSubCostCenter(data.getRequiresSubCostCenter());

        existing.setParent(data.getParent());

        return repository.save(existing);
    }

    // =====================================================
    // ENABLE / DISABLE (ERP REAL)
    // =====================================================
    public void deactivate(Long id) {
        ChartOfAccounts account = findById(id);
        account.setActive(false);
    }

    public void activate(Long id) {
        ChartOfAccounts account = findById(id);
        account.setActive(true);
    }
}
