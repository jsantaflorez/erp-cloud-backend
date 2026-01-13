package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.ChartOfAccountRequest;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.security.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ChartOfAccountsService {

    private final ChartOfAccountsRepository repository;
    private final CompanyContext companyContext;

    /**
     * Create a new account entry using DTO
     */
    public ChartOfAccounts create(ChartOfAccountRequest request) {
        Company company = companyContext.getCurrentCompany();

        if (repository.existsByCompanyAndCode(company, request.getCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Account code " + request.getCode() + " already exists");
        }

        ChartOfAccounts account = new ChartOfAccounts();
        // Manual mapping from DTO to Entity
        mapDtoToEntity(request, account);

        account.setCompany(company);
        account.setActive(true);

        // Handle hierarchy if parentId is provided
        if (request.getParentId() != null) {
            ChartOfAccounts parent = repository.findById(request.getParentId())
                    .filter(p -> p.getCompany().getId().equals(company.getId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent account not found"));
            account.setParent(parent);
        }

        return repository.save(account);
    }


    /**
     * Read and Search operations
     */
    @Transactional(readOnly = true)
    public ChartOfAccounts findByCode(String code) {
        Company company = companyContext.getCurrentCompany();
        return repository.findByCompanyAndCode(company, code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    @Transactional(readOnly = true)
     public ChartOfAccounts findById(Long id) {
        Company company = companyContext.getCurrentCompany();
        return repository.findById(id)
                .filter(acc -> acc.getCompany().getId().equals(company.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    @Transactional(readOnly = true)
    public List<ChartOfAccounts> listRoots() {
        Company company = companyContext.getCurrentCompany();
        return repository.findByCompanyAndParentIsNullOrderByCodeAsc(company);
    }

    @Transactional(readOnly = true)
    public List<ChartOfAccounts> listChildren(Long parentId) {
        Company company = companyContext.getCurrentCompany();
        return repository.findByCompanyAndParentIdOrderByCodeAsc(company, parentId);
    }

    @Transactional(readOnly = true)
    public List<ChartOfAccounts> listPostingAccounts() {
        Company company = companyContext.getCurrentCompany();
        return repository.findByCompanyAndPostingAccountTrueAndActiveTrueOrderByCodeAsc(company);
    }

    @Transactional(readOnly = true)
    public List<ChartOfAccounts> search(String text) {
        Company company = companyContext.getCurrentCompany();
        return repository.findByCompanyAndNameContainingIgnoreCaseOrCompanyAndCodeContainingIgnoreCase(
                company, text, company, text
        );
    }

    @Transactional(readOnly = true)
    public List<ChartOfAccounts> listByLevel(Byte level) {
        Company company = companyContext.getCurrentCompany();
        return repository.findByCompanyAndLevelAndActiveTrueOrderByCodeAsc(company, level);
    }


    /**
     * List all accounts for the current company (Full Catalog)
     */
    @Transactional(readOnly = true)
    public List<ChartOfAccounts> listAll() {
        Company company = companyContext.getCurrentCompany();

        // We order by code so the hierarchy makes sense visually in a flat list
        return repository.findByCompanyOrderByCodeAsc(company);
    }

    /**
     * Update existing account using DTO
     */
    public ChartOfAccounts update(Long id, ChartOfAccountRequest request) {
        ChartOfAccounts existing = findById(id);

        // Update basic fields
        mapDtoToEntity(request, existing);

        // Update parent if changed
        if (request.getParentId() != null) {
            ChartOfAccounts parent = repository.findById(request.getParentId())
                    .filter(p -> p.getCompany().getId().equals(existing.getCompany().getId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid parent account"));
            existing.setParent(parent);
        } else {
            existing.setParent(null);
        }

        return repository.save(existing);
    }

    /**
     * Helper method for common mapping
     */
    private void mapDtoToEntity(ChartOfAccountRequest dto, ChartOfAccounts entity) {
        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        entity.setLevel(dto.getLevel());
        entity.setNature(dto.getNature());
        entity.setAccountClass(dto.getAccountClass());
        entity.setAccountType(dto.getAccountType());
        entity.setPostingAccount(dto.getPostingAccount());
        entity.setRequiresThirdParty(dto.getRequiresThirdParty());
        entity.setRequiresCostCenter(dto.getRequiresCostCenter());
        entity.setRequiresSubCostCenter(dto.getRequiresSubCostCenter());
    }
    /**
     * Logical activation/deactivation
     */
    public void deactivate(Long id) {
        ChartOfAccounts account = findById(id);
        account.setActive(false);
    }

    public void activate(Long id) {
        ChartOfAccounts account = findById(id);
        account.setActive(true);
    }
}
