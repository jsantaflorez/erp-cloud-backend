package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.ChartOfAccountRequest;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.dto.ChartOfAccountResponseDTO;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.exception.DuplicateResourceException;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.exception.ResourceNotFoundException;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.security.context.TenantContext;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ChartOfAccountService {

    private static final Logger log = LoggerFactory.getLogger(ChartOfAccountService.class);

    private final ChartOfAccountsRepository repository;
    private final TenantContext companyContext;

    /**
     * Create a new account entry with strict accounting structure validations.
     */
    public ChartOfAccountResponseDTO create(ChartOfAccountRequest request) {
        Company company = companyContext.getCurrentCompany();

        log.debug("Creating chart of account with code: {} for company: {}", request.getCode(), company.getId());

        // 1. Check for duplicate code within the same company
        if (repository.existsByCompanyAndCode(company, request.getCode())) {
            throw new DuplicateResourceException("ChartOfAccount", "code", request.getCode());
        }

        ChartOfAccounts account = new ChartOfAccounts();
        ChartOfAccounts parent = null;

        // 2. Handle Hierarchy and Parent validation
        if (request.getParentId() != null) {
            parent = repository.findById(request.getParentId())
                    .filter(p -> p.getCompany().getId().equals(company.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("ChartOfAccount (parent)", request.getParentId()));

            // 2.1 BUSINESS RULE: Auxiliary accounts (posting) cannot have children
            if (parent.isPostingAccount()) {
                throw new InvalidOperationException("Cannot add children to a posting account");
            }

            // 2.2 BUSINESS RULE: All children must belong to the same Account Class as their parent.
            validateHierarchyConsistency(parent, request);

            account.setParent(parent);
            account.setLevel((byte) (parent.getLevel() + 1));
        } else {
            account.setParent(null);
            account.setLevel((byte) 1);
        }

        // 3. Validate code length and posting rules
        validateCodeStructure(parent, request);

        // 4. Map DTO to Entity and save
        mapDtoToEntity(request, account);
        account.setCompany(company);
        account.setActive(true);

        ChartOfAccounts saved = repository.save(account);
        log.info("Chart of account created successfully with id: {} and code: {}", saved.getId(), saved.getCode());

        return mapToResponseDTO(saved);
    }

    public ChartOfAccountResponseDTO update(Long id, ChartOfAccountRequest request) {
        log.debug("Updating chart of account id: {}", id);

        // 1. Find existing account and verify company ownership
        ChartOfAccounts existing = findEntityById(id);

        // 2. Code change is strictly forbidden in accounting
        if (!existing.getCode().equals(request.getCode())) {
            throw new InvalidOperationException(
                    "The account code cannot be modified. If the code is wrong, you must create a new one."
            );
        }


        // 3. Inverse Cascade Validation
        if (Boolean.TRUE.equals(request.getPostingAccount()) && !existing.isPostingAccount()) {
            if (repository.existsByParent(existing)) {
                throw new InvalidOperationException("Cannot change to posting account; it has children.");
            }
        }

        // 4. Account class changes are allowed even with transactions
        // BUSINESS RULE: We intentionally do NOT check for journal entries when changing account class.
        // Rationale: Accountants may need to reclassify accounts retroactively for reporting purposes.
        // Historical transactions remain unchanged; only future reporting is affected.
       if (!existing.getAccountClass().equals(request.getAccountClass())) {
            log.warn("Account class changed from {} to {} for account {} (id: {}). " +
                            "This affects reporting but does not modify historical transactions.",
                    existing.getAccountClass(), request.getAccountClass(), existing.getCode(), id);
        }


        // 5. Handle Hierarchy and Context
        ChartOfAccounts parentToValidate;

        if (request.getParentId() != null) {
            // Case A: User is moving the account to a NEW parent
            if (id.equals(request.getParentId())) {
                throw new InvalidOperationException("An account cannot be its own parent");
            }

            // Prevent circular references - Load the potential parent
            parentToValidate = repository.findById(request.getParentId())
                    .filter(p -> p.getCompany().getId().equals(existing.getCompany().getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("ChartOfAccount (parent)", request.getParentId()));

            // CIRCULAR CHECK: Use the object we just loaded
            if (isDescendant(id, parentToValidate)) {
                throw new InvalidOperationException("Circular reference: Cannot move under a descendant.");
            }

            if (parentToValidate.isPostingAccount()) {
                throw new InvalidOperationException("Parent cannot be a posting account");
            }

            validateHierarchyConsistency(parentToValidate, request);

            existing.setParent(parentToValidate);
            existing.setLevel((byte) (parentToValidate.getLevel() + 1));
        } else {
            // Case B: User did NOT send parentId. Use the existing one from DB
            parentToValidate = existing.getParent();

            // If it's not a Level 1 root, maintain its current level
            if (parentToValidate == null) {
                existing.setLevel((byte) 1);
            } else {
                existing.setLevel((byte) (parentToValidate.getLevel() + 1));
            }
        }

        // 6. Validate code structure (parentToValidate is never null unless it's truly Level 1)
        validateCodeStructure(parentToValidate, request);

        // 7. Map the rest of the fields
        mapDtoToEntity(request, existing);

        ChartOfAccounts updated = repository.save(existing);
        log.info("Chart of account {} updated successfully", id);

        return mapToResponseDTO(updated);
    }

    // TODO: It will be possible to delete an account, but it is necessary to validate all possibilities
    // public void delete(Long id) {
    //     // 1. Find existing account
    //     ChartOfAccounts existing = findEntityById(id);
    //
    //     // 2. BLOCK: Prevent deleting accounts with children
    //     boolean hasChildren = repository.existsByParent(existing);
    //     if (hasChildren) {
    //         throw new InvalidOperationException(
    //             "Cannot delete account because it has sub-accounts (children)."
    //         );
    //     }
    //
    //     // 3. TODO: Check for accounting movements
    //     // if (movementRepository.existsByAccount(account)) {
    //     //    throw new InvalidOperationException("Account has movements");
    //     // }
    //
    //     // 4. TODO: Check for non-zero initial balances
    //     // if (balanceRepository.hasInitialBalance(account)) {
    //     //    throw new InvalidOperationException("Account has initial balances");
    //     // }
    //
    //     repository.delete(existing);
    //     log.info("Chart of account {} deleted successfully", id);
    // }

    /**
     * Read and Search operations
     */
    @Transactional(readOnly = true)
    public Page<ChartOfAccountResponseDTO> listAll(String searchTerm, Pageable pageable) {
        Company company = companyContext.getCurrentCompany();

        log.debug("Listing chart of accounts for company: {} with search term: {}", company.getId(), searchTerm);

        Page<ChartOfAccounts> entities = (searchTerm != null && !searchTerm.trim().isEmpty())
                ? repository.searchByText(company, searchTerm, pageable)
                : repository.findByCompany(company, pageable);

        return entities.map(this::mapToResponseDTO);
    }

    @Transactional(readOnly = true)
    public ChartOfAccountResponseDTO findById(Long id) {
        return mapToResponseDTO(findEntityById(id));
    }

    /**
     * Internal helper to get the Entity for internal Service use
     */
    private ChartOfAccounts findEntityById(Long id) {
        Company company = companyContext.getCurrentCompany();

        log.debug("Finding chart of account by id: {} for company: {}", id, company.getId());

        return repository.findById(id)
                .filter(acc -> acc.getCompany().getId().equals(company.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("ChartOfAccount", id));
    }

    /**
     * Methods for specific UI needs (Tree view, etc.)
     */
    @Transactional(readOnly = true)
    public List<ChartOfAccountResponseDTO> listRoots() {
        Company company = companyContext.getCurrentCompany();

        log.debug("Listing root chart of accounts for company: {}", company.getId());

        return repository.findByCompanyAndParentIsNullOrderByCodeAsc(company)
                .stream()
                .map(this::mapToResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChartOfAccountResponseDTO findByCode(String code) {
        Company company = companyContext.getCurrentCompany();

        log.debug("Finding chart of account by code: {} for company: {}", code, company.getId());

        ChartOfAccounts entity = repository.findByCompanyAndCode(company, code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("ChartOfAccount with code '%s' not found", code)
                ));

        return mapToResponseDTO(entity);
    }

    @Transactional(readOnly = true)
    public List<ChartOfAccountResponseDTO> listChildren(Long parentId) {
        Company company = companyContext.getCurrentCompany();

        log.debug("Listing children chart of accounts for parent id: {}", parentId);

        return repository.findByCompanyAndParentIdOrderByCodeAsc(company, parentId)
                .stream()
                .map(this::mapToResponseDTO)
                .toList();
    }

    /**
     * Retrieves all accounts enabled for posting (auxiliaries)
     * with pagination. Useful for accounting entry selectors.
     */
    @Transactional(readOnly = true)
    public Page<ChartOfAccountResponseDTO> listPostingAccounts(Pageable pageable) {
        Company company = companyContext.getCurrentCompany();

        log.debug("Listing posting accounts for company: {}", company.getId());

        // Uses the paginated query from the repository
        return repository.findPostingAccounts(company, pageable)
                .map(this::mapToResponseDTO);
    }

    /**
     * Retrieves active accounts filtered by level with pagination support.
     */
    @Transactional(readOnly = true)
    public Page<ChartOfAccountResponseDTO> listByLevel(Byte level, Pageable pageable) {
        Company company = companyContext.getCurrentCompany();

        log.debug("Listing chart of accounts by level: {} for company: {}", level, company.getId());

        return repository.findByCompanyAndLevelAndActiveTrue(company, level, pageable)
                .map(this::mapToResponseDTO);
    }

    /**
     * Deactivates an account to prevent future use.
     *
     * BUSINESS RULE: Accounts can be deactivated even if they have journal entries.
     * This is intentional to allow accountants to "close" accounts that should no longer
     * be used while preserving historical data integrity.
     *
     * Deactivated accounts:
     * - Cannot be used in new journal entries (enforced at journal entry creation)
     * - Remain visible in reports for historical transactions
     * - Can be reactivated if needed
     *
     * NOTE: We do NOT check for existing transactions before deactivation.
     */
    public void deactivate(Long id) {
        log.debug("Deactivating chart of account id: {}", id);

        ChartOfAccounts account = findEntityById(id);

        // Optional: Check if account has children (business decision)
        if (repository.existsByParent(account)) {
            log.warn("Deactivating account {} which has child accounts. " +
                    "Consider deactivating children first.", account.getCode());
            // NOTE: We log but allow the operation.
            // Alternative: throw exception to enforce hierarchy deactivation
        }

        account.setActive(false);
        repository.save(account);

        log.info("Chart of account {} ({}) deactivated successfully. " +
                "Historical transactions are preserved.", id, account.getCode());
    }

    /**
     * Reactivates a previously deactivated account.
     */
    public void activate(Long id) {
        log.debug("Activating chart of account id: {}", id);

        ChartOfAccounts account = findEntityById(id);

        // Validate parent is active before reactivating child
        if (account.getParent() != null && !account.getParent().isActive()) {
            throw new InvalidOperationException(
                    String.format("Cannot activate account %s because its parent account %s is inactive. " +
                                    "Please activate the parent first.",
                            account.getCode(), account.getParent().getCode())
            );
        }

        account.setActive(true);
        repository.save(account);

        log.info("Chart of account {} activated successfully", id);
    }





    /**
     * Validates the accounting code length based on PUC standards:
     * Level 1: 1 digit
     * Level 2: 2 digits
     * Level 3: 4 digits
     * Level 4+: Parent length + 2 (Min 6 digits for posting)
     */
    private void validateCodeStructure(ChartOfAccounts parent, ChartOfAccountRequest request) {
        String code = request.getCode();
        int codeLength = code.length();

        // Rule: Posting accounts must have at least 6 digits (Level 4)
        if (Boolean.TRUE.equals(request.getPostingAccount()) && codeLength < 6) {
            throw new InvalidOperationException(
                    String.format("Invalid account code: Posting accounts require at least 6 digits (Level 4 or deeper). Provided: %d digits", codeLength)
            );
        }

        // Root validation: If there is no parent, it MUST be Level 1 (1 digit)
        if (parent == null) {
            if (codeLength != 1) {
                throw new InvalidOperationException(
                        String.format("Root accounts must have exactly 1 digit. Provided: %s", code)
                );
            }
            return;
        }

        String parentCode = parent.getCode();
        int parentLength = parentCode.length();

        // Verify code starts with parent code
        if (!code.startsWith(parentCode)) {
            throw new InvalidOperationException(
                    String.format("Child code '%s' must start with parent code '%s'", code, parentCode)
            );
        }

        boolean isValidJump = false;
        // PUC structure: 1 -> 2 -> 4 -> 6...
        if (parentLength == 1 && codeLength == 2) isValidJump = true;
        else if (parentLength == 2 && codeLength == 4) isValidJump = true;
        else if (parentLength >= 4 && codeLength == parentLength + 2) isValidJump = true;

        if (!isValidJump) {
            throw new InvalidOperationException(
                    String.format("Invalid code structure. Parent '%s' (%d digits) requires child to be %d digits. Provided: %d digits",
                            parentCode, parentLength, getExpectedLength(parentLength), codeLength)
            );
        }
    }

    private int getExpectedLength(int parentLength) {
        if (parentLength == 1) return 2;
        if (parentLength == 2) return 4;
        return parentLength + 2;
    }

    private boolean isDescendant(Long accountId, ChartOfAccounts currentParent) {
        log.debug("Checking circular reference for account id: {} with parent: {}",
                accountId, currentParent != null ? currentParent.getId() : null);

        while (currentParent != null) {
            if (accountId.equals(currentParent.getId())) {
                log.warn("Circular reference detected: account {} is a descendant of itself", accountId);
                return true;
            }
            currentParent = currentParent.getParent();
        }
        return false;
    }

    /**
     * Validates hierarchy consistency.
     * All children must belong to the same Account Class as their parent.
     * Nature (Debit/Credit) is NOT validated here to allow contra-accounts.
     */
    private void validateHierarchyConsistency(ChartOfAccounts parent, ChartOfAccountRequest request) {
        if (parent == null) return;

        // We compare strings to avoid Enum vs String mismatch issues
        String parentClass = parent.getAccountClass().toString();
        String childClass = request.getAccountClass().toString();

        if (!parentClass.equals(childClass)) {
            log.error("Hierarchy mismatch: Parent {} is {}, but requested child is {}",
                    parent.getCode(), parentClass, childClass);

            throw new InvalidOperationException(
                    String.format("Account class mismatch. The parent account '%s' is %s. " +
                                    "All sub-accounts must share the same class.",
                            parent.getCode(), parentClass)
            );
        }
    }

    /**
     * Helper method for common mapping.
     */
    private void mapDtoToEntity(ChartOfAccountRequest dto, ChartOfAccounts entity) {
        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        entity.setNature(dto.getNature());
        entity.setAccountClass(dto.getAccountClass());
        entity.setAccountCategory(dto.getAccountCategory());
        entity.setFinancialStatement(dto.getFinancialStatement());
        entity.setPostingAccount(dto.getPostingAccount());
        entity.setRequiresThirdParty(dto.getRequiresThirdParty());
        entity.setRequiresCostCenter(dto.getRequiresCostCenter());

        if (dto.getActive() != null) {
            entity.setActive(dto.getActive());
        }

        // displayOrder and closesAtYearEnd are auto-set based on category/statement
        // They're set in @PrePersist/@PreUpdate
    }

    private ChartOfAccountResponseDTO mapToResponseDTO(ChartOfAccounts entity) {
        if (entity == null) {
            return null;
        }

        ChartOfAccountResponseDTO dto = new ChartOfAccountResponseDTO();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setLevel(entity.getLevel());
        dto.setNature(entity.getNature());

        // Enhanced fields
        dto.setAccountClass(entity.getAccountClass());
        dto.setAccountCategory(entity.getAccountCategory());
        dto.setAccountCategoryDisplay(entity.getCategoryDisplayName());
        dto.setFinancialStatement(entity.getFinancialStatement());
        dto.setFinancialStatementDisplay(entity.getFinancialStatementDisplayName());
        dto.setClosesAtYearEnd(entity.isClosesAtYearEnd());
        dto.setDisplayOrder(entity.getDisplayOrder());

        // Business rules
        dto.setPostingAccount(entity.isPostingAccount());
        dto.setRequiresThirdParty(entity.isRequiresThirdParty());
        dto.setRequiresCostCenter(entity.isRequiresCostCenter());
        dto.setActive(entity.isActive());

        // Hierarchy
        if (entity.getParent() != null) {
            dto.setParentCode(entity.getParent().getCode());
            dto.setParentName(entity.getParent().getName());
        }

        dto.setFullDescription(entity.getFullDescription());

        return dto;
    }
}