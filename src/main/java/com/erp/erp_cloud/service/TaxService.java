package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.TaxRequest;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.Tax;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.repository.TaxRepository;
import com.erp.erp_cloud.security.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaxService {

    private final TaxRepository repository;
    private final ChartOfAccountsRepository accountRepository;
    private final CompanyContext companyContext;

    @Transactional(readOnly = true)
    public List<Tax> listAll() {
        return repository.findByCompany(companyContext.getCurrentCompany());
    }

    @Transactional
    public Tax create(TaxRequest request) {
        Company company = companyContext.getCurrentCompany();

        // 1. Validate that the account exists and belongs to the current company
        ChartOfAccounts account = accountRepository.findById(request.getAccountId())
                .filter(a -> a.getCompany().getId().equals(company.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Accounting account not found or does not belong to your company."));

        // 2. Strict rule: One account can only be linked to ONE tax rule per company
        if (repository.existsByCompanyAndAccount(company, account)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Account " + account.getCode() + " is already linked to another tax rule.");
        }

        // 3. Tax code uniqueness within the company
        if (repository.existsByCompanyAndCode(company, request.getCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tax code '" + request.getCode() + "' already exists.");
        }

        Tax tax = new Tax();
        mapRequestToEntity(request, tax);
        tax.setAccount(account);
        tax.setCompany(company);

        return repository.save(tax);
    }

    @Transactional
    public Tax update(Long id, TaxRequest request) {
        Tax existingTax = findById(id);
        Company company = companyContext.getCurrentCompany();

        // Validate code uniqueness if it's being changed
        if (!existingTax.getCode().equals(request.getCode()) &&
                repository.existsByCompanyAndCode(company, request.getCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New tax code already in use.");
        }

        // Account changes are tricky in taxes; for now, we update basic fields
        mapRequestToEntity(request, existingTax);

        // If account changes, we should re-validate the unique constraint
        if (!existingTax.getAccount().getId().equals(request.getAccountId())) {
            ChartOfAccounts newAccount = accountRepository.findById(request.getAccountId())
                    .filter(a -> a.getCompany().getId().equals(company.getId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "New account not found."));

            if (repository.existsByCompanyAndAccount(company, newAccount)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New account already has a tax linked.");
            }
            existingTax.setAccount(newAccount);
        }

        return repository.save(existingTax);
    }

    @Transactional(readOnly = true)
    public Tax findById(Long id) {
        return repository.findById(id)
                .filter(t -> t.getCompany().getId().equals(companyContext.getCurrentCompany().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tax rule not found."));
    }

    private void mapRequestToEntity(TaxRequest request, Tax tax) {
        tax.setCode(request.getCode());
        tax.setName(request.getName());
        tax.setType(request.getType());
        tax.setRate(request.getRate());
        tax.setSign(request.getSign());
        tax.setRequiresBase(request.isRequiresBase());
        tax.setMinimumBase(request.getMinimumBase());
    }
}