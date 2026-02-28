package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.TaxRequest;
import com.erp.erp_cloud.dto.TaxResponseDTO;
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
@Transactional(readOnly = true)
public class TaxService {

    private final TaxRepository repository;
    private final ChartOfAccountsRepository accountRepository;
    private final CompanyContext companyContext;

    public List<TaxResponseDTO> listAll() {
        return repository.findByCompany(companyContext.getCurrentCompany())
                .stream()
                .map(this::mapToResponseDTO)
                .toList();
    }

    @Transactional
    public TaxResponseDTO create(TaxRequest request) {
        Company company = companyContext.getCurrentCompany();

        // Account check with Multi-tenant filter
        ChartOfAccounts account = accountRepository.findById(request.getAccountId())
                .filter(a -> a.getCompany().getId().equals(company.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));

        if (repository.existsByCompanyAndAccount(company, account)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account already linked to a tax.");
        }

        Tax tax = new Tax();
        mapRequestToEntity(request, tax);
        tax.setAccount(account);
        tax.setCompany(company);

        return mapToResponseDTO(repository.save(tax));
    }

    @Transactional
    public TaxResponseDTO update(Long id, TaxRequest request) {
        Tax existing = findEntityById(id);
        Company company = companyContext.getCurrentCompany();

        // Code uniqueness check
        if (!existing.getCode().equals(request.getCode()) &&
                repository.existsByCompanyAndCode(company, request.getCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code already in use.");
        }

        mapRequestToEntity(request, existing);

        // Account change logic
        if (!existing.getAccount().getId().equals(request.getAccountId())) {
            ChartOfAccounts newAccount = accountRepository.findById(request.getAccountId())
                    .filter(a -> a.getCompany().getId().equals(company.getId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));

            if (repository.existsByCompanyAndAccount(company, newAccount)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New account already linked.");
            }
            existing.setAccount(newAccount);
        }

        return mapToResponseDTO(repository.save(existing));
    }

    public Tax findEntityById(Long id) {
        return repository.findById(id)
                .filter(t -> t.getCompany().getId().equals(companyContext.getCurrentCompany().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tax not found."));
    }

    private void mapRequestToEntity(TaxRequest request, Tax tax) {
        tax.setCode(request.getCode());
        tax.setName(request.getName());
        tax.setType(request.getType().toUpperCase()); // Ensure consistency
        tax.setRate(request.getRate());
        tax.setSign(request.getSign().toUpperCase());
        tax.setRequiresBase(request.isRequiresBase());
        tax.setMinimumBase(request.getMinimumBase());
    }

    private TaxResponseDTO mapToResponseDTO(Tax entity) {
        TaxResponseDTO dto = new TaxResponseDTO();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setType(entity.getType());
        dto.setRate(entity.getRate());
        dto.setRequiresBase(entity.isRequiresBase());
        dto.setMinimumBase(entity.getMinimumBase());
        dto.setSign(entity.getSign());
        dto.setActive(entity.isActive());

        if (entity.getAccount() != null) {
            dto.setAccountCode(entity.getAccount().getCode());
            dto.setAccountName(entity.getAccount().getName());
        }

        dto.setFullTaxDescription(String.format("%s - %s (%s%%)",
                entity.getCode(), entity.getName(), entity.getRate()));

        return dto;
    }
}