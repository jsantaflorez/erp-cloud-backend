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
     * Create a new account entry with strict accounting structure validations.
     */
    public ChartOfAccounts create(ChartOfAccountRequest request) {
        Company company = companyContext.getCurrentCompany();

        // 1. Check for duplicate code within the same company
        if (repository.existsByCompanyAndCode(company, request.getCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Account code " + request.getCode() + " already exists");
        }

        ChartOfAccounts account = new ChartOfAccounts();
        ChartOfAccounts parent = null;

        // 2. Handle Hierarchy and Parent validation
        if (request.getParentId() != null) {
            parent = repository.findById(request.getParentId())
                    .filter(p -> p.getCompany().getId().equals(company.getId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent account not found"));

            // BUSINESS RULE: Auxiliary accounts (posting) cannot have children
            if (Boolean.TRUE.equals(parent.getPostingAccount())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cannot add children to a posting account");
            }

            account.setParent(parent);
            account.setLevel((byte) (parent.getLevel() + 1));
        } else {
            account.setParent(null);
            account.setLevel((byte) 1);
        }

        // 3. NEW: Validate code length structure (1 -> 2 -> 4 -> 6)
        validateCodeStructure(parent, request.getCode());

        // 4. Map DTO to Entity and save
        mapDtoToEntity(request, account);
        account.setCompany(company);
        account.setActive(true);

        return repository.save(account);
    }

    /**
     * Validates the accounting code length based on PUC standards:
     * Level 1: 1 digit
     * Level 2: 2 digits
     * Level 3: 4 digits
     * Level 4+: Parent length + 2
     */
    private void validateCodeStructure(ChartOfAccounts parent, String code) {
        int codeLength = code.length();

        // Root validation (Level 1)
        if (parent == null) {
            if (codeLength != 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Root accounts (Level 1) must have a 1-digit code (e.g., '1')");
            }
            return;
        }

        int parentLength = parent.getCode().length();
        boolean isValidJump = false;

        // Jump from Level 1 to Level 2 (1 digit -> 2 digits)
        if (parentLength == 1 && codeLength == 2) isValidJump = true;
            // Jump from Level 2 to Level 3 (2 digits -> 4 digits)
        else if (parentLength == 2 && codeLength == 4) isValidJump = true;
            // Jump from Level 3 onwards (Current + 2 digits)
        else if (parentLength >= 4 && codeLength == parentLength + 2) isValidJump = true;

        if (!isValidJump) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid code length for the selected parent. Structure must follow: 1 -> 2 -> 4 -> 6 digits.");
        }
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
        // Use the new, cleaner repository method
        return repository.findPostingAccounts(company);
    }

    @Transactional(readOnly = true)
    public List<ChartOfAccounts> search(String text) {
        Company company = companyContext.getCurrentCompany();
        return repository.searchByText(company, text);
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
     * Update existing account details with business rules
     */
    public ChartOfAccounts update(Long id, ChartOfAccountRequest request) {
        // 1. Find existing account and verify company ownership
        ChartOfAccounts existing = findById(id);

        // 2. Validate Code Uniqueness if it's being changed
        if (!existing.getCode().equals(request.getCode())) {
            boolean codeExists = repository.existsByCompanyAndCode(existing.getCompany(), request.getCode());
            if (codeExists) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Account code " + request.getCode() + " is already in use");
            }
        }

        // 3. Handle Hierarchy and Level recalculation (This sets the level)
        if (request.getParentId() != null) {
            if (id.equals(request.getParentId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An account cannot be its own parent");
            }

            ChartOfAccounts parent = repository.findById(request.getParentId())
                    .filter(p -> p.getCompany().getId().equals(existing.getCompany().getId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid parent account"));

            if (Boolean.TRUE.equals(parent.getPostingAccount())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent cannot be a posting account");
            }

            existing.setParent(parent);
            existing.setLevel((byte) (parent.getLevel() + 1));
        } else {
            existing.setParent(null);
            existing.setLevel((byte) 1);
        }

        // 4. Update the rest of the fields (Careful: this must not overwrite 'level')
        mapDtoToEntity(request, existing);

        return repository.save(existing);
    }

    /**
     * Helper method for common mapping.
     * LEVEL is excluded because it's calculated by the Service logic.
     */
    private void mapDtoToEntity(ChartOfAccountRequest dto, ChartOfAccounts entity) {
        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        // entity.setLevel is NOT set here to avoid null overwrites
        entity.setNature(dto.getNature());
        entity.setAccountClass(dto.getAccountClass());
        entity.setAccountType(dto.getAccountType());
        entity.setPostingAccount(dto.getPostingAccount());
        entity.setRequiresThirdParty(dto.getRequiresThirdParty());
        entity.setRequiresCostCenter(dto.getRequiresCostCenter());
        entity.setRequiresSubCostCenter(dto.getRequiresSubCostCenter());

        if (dto.getActive() != null) {
            entity.setActive(dto.getActive());
        }
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
