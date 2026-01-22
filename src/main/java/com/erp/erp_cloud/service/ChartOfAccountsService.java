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

            // 2.1 BUSINESS RULE: Auxiliary accounts (posting) cannot have children
            if (Boolean.TRUE.equals(parent.getPostingAccount())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cannot add children to a posting account");
            }
            // 2.2 BUSINESS RULE: Consistency check - Child must match parent's class [cite: 2026-01-14]
            if (!parent.getAccountClass().equals(request.getAccountClass())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Account class mismatch. Expected: " + parent.getAccountClass());
            }
            account.setParent(parent);
            account.setLevel((byte) (parent.getLevel() + 1));
        } else {
            account.setParent(null);
            account.setLevel((byte) 1);
        }

        // 3. NEW: Validate code length and posting rules [cite: 2026-01-14]
        validateCodeStructure(parent, request);

        // 4. Map DTO to Entity and save
        mapDtoToEntity(request, account);
        account.setCompany(company);
        account.setActive(true);

        return repository.save(account);
    }

    /**
     * Update existing account details with business rules.
     */

    public ChartOfAccounts update(Long id, ChartOfAccountRequest request) {
        // 1. Find existing account and verify company ownership
        ChartOfAccounts existing = findById(id);

        // 2. BLOCK: Code change is strictly forbidden in accounting [cite: 2026-01-14]
        if (!existing.getCode().equals(request.getCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The account code cannot be modified. If the code is wrong, you must create a new one.");
        }

        // 3. Handle Hierarchy and Level recalculation
        ChartOfAccounts parent = null;
        if (request.getParentId() != null) {
            if (id.equals(request.getParentId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An account cannot be its own parent");
            }

            parent = repository.findById(request.getParentId())
                    .filter(p -> p.getCompany().getId().equals(existing.getCompany().getId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid parent account"));

            if (Boolean.TRUE.equals(parent.getPostingAccount())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent cannot be a posting account");
            }

            // 3.1 NEW: Ensure class consistency with parent even on update [cite: 2026-01-14]
            if (!parent.getAccountClass().equals(request.getAccountClass())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Account class mismatch. Expected: " + parent.getAccountClass());
            }

            existing.setParent(parent);
            existing.setLevel((byte) (parent.getLevel() + 1));
        } else {
            existing.setParent(null);
            existing.setLevel((byte) 1);
        }

        // 4. Validate code structure for the update
        validateCodeStructure(parent, request);

        // 5. Update the rest of the fields
        mapDtoToEntity(request, existing);

        return repository.save(existing);

        // 6. TODO: Safety check for accounting flags [cite: 2026-01-17]
        // If the user is trying to DISABLE a requirement (ThirdParty, CostCenter, etc.)
      //  if (existing.getRequiresThirdParty() && !request.getRequiresThirdParty()) {
            // if (movementRepository.existsWithThirdParty(existing)) {
            //    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            //        "Cannot disable Third Party requirement because movements already exist.");
            // }
      //  }

        // Repeat the logic for CostCenter and SubCostCenter [cite: 2026-01-17]
    }
    public void delete(Long id) {
        // 1. Find existing account
        ChartOfAccounts account = findById(id);

        // 2. BLOCK: Prevent deleting accounts with children [cite: 2026-01-14]
        boolean hasChildren = repository.existsByParent(account);
        if (hasChildren) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot delete account because it has sub-accounts (children).");
        }

        // 3. TODO: Check for accounting movements [cite: 2026-01-17]
        // if (movementRepository.existsByAccount(account)) {
        //    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account has movements");
        // }

        // 4. TODO: Check for non-zero initial balances [cite: 2026-01-17]
        // if (balanceRepository.hasInitialBalance(account)) {
        //    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account has initial balances");
        // }

        repository.delete(account);
    }



    /**
     * Validates the accounting code length based on PUC standards:
     * Level 1: 1 digit
     * Level 2: 2 digits
     * Level 3: 4 digits
     * Level 4+: Parent length + 2 (Min 6 digits for posting) [cite: 2026-01-14]
     */
    private void validateCodeStructure(ChartOfAccounts parent, ChartOfAccountRequest request) {
        String code = request.getCode();
        int codeLength = code.length();

        // Rule: Posting accounts must have at least 6 digits (Level 4) [cite: 2026-01-14]
        if (Boolean.TRUE.equals(request.getPostingAccount()) && codeLength < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Posting accounts (auxiliaries) must have at least 6 digits (Level 4)");
        }

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

        // PUC structure: 1 -> 2 -> 4 -> 6...
        if (parentLength == 1 && codeLength == 2) isValidJump = true;
        else if (parentLength == 2 && codeLength == 4) isValidJump = true;
        else if (parentLength >= 4 && codeLength == parentLength + 2) isValidJump = true;

        if (!isValidJump) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid code length for the selected parent. Structure: 1 -> 2 -> 4 -> 6 digits.");
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

    @Transactional(readOnly = true)
    public List<ChartOfAccounts> listAll() {
        Company company = companyContext.getCurrentCompany();
        return repository.findByCompanyOrderByCodeAsc(company);
    }

    /**
     * Helper method for common mapping.
     */
    private void mapDtoToEntity(ChartOfAccountRequest dto, ChartOfAccounts entity) {
        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
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

    public void deactivate(Long id) {
        ChartOfAccounts account = findById(id);
        account.setActive(false);
    }

    public void activate(Long id) {
        ChartOfAccounts account = findById(id);
        account.setActive(true);
    }
}