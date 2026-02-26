package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.ChartOfAccountRequest;

import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.dto.ChartOfAccountResponseDTO;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.security.context.CompanyContext;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;



import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ChartOfAccountService {

    private final ChartOfAccountsRepository repository;
    private final CompanyContext companyContext;

    /**
     * Create a new account entry with strict accounting structure validations.
     */



    public ChartOfAccountResponseDTO create(ChartOfAccountRequest request) {
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
            if (parent.isPostingAccount()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot add children to a posting account");
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

        // 3. Validate code length and posting rules [cite: 2026-01-14]
        validateCodeStructure(parent, request);

        // 4. Map DTO to Entity and save
        mapDtoToEntity(request, account);
        account.setCompany(company);
        account.setActive(true);
        return mapToResponseDTO(repository.save(account));
    }
    public ChartOfAccountResponseDTO update(Long id, ChartOfAccountRequest request) {
        // 1. Find existing account and verify company ownership
        ChartOfAccounts existing = findEntityById(id);

        // 2. Code change is strictly forbidden in accounting
        if (!existing.getCode().equals(request.getCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The account code cannot be modified. If the code is wrong, you must create a new one.");
        }

        // 3. Inverse Cascade Validation

        if (Boolean.TRUE.equals(request.getPostingAccount()) && !existing.isPostingAccount()) {
            if (repository.existsByParent(existing)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot change to posting account; it has children.");
            }
        }

        //  4. Handle Hierarchy and Context ---
        ChartOfAccounts parentToValidate;

        if (request.getParentId() != null) {
            // Case A: User is moving the account to a NEW parent
            if (id.equals(request.getParentId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An account cannot be its own parent");
            }

       // Prevent circular references
       // Load the potential parent
            parentToValidate = repository.findById(request.getParentId())
                    .filter(p -> p.getCompany().getId().equals(existing.getCompany().getId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid parent account"));

            // CIRCULAR CHECK: Use the object we just loaded
            if (isDescendant(id, parentToValidate)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Circular reference: Cannot move under a descendant.");
            }




            parentToValidate = repository.findById(request.getParentId())
                    .filter(p -> p.getCompany().getId().equals(existing.getCompany().getId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid parent account"));


            if (parentToValidate != null && parentToValidate.isPostingAccount()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent cannot be a posting account");
            }



            if (!parentToValidate.getAccountClass().equals(request.getAccountClass())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Account class mismatch. Expected: " + parentToValidate.getAccountClass());
            }

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

        // 5. Validate code structure (parentToValidate is never null unless it's truly Level 1)
        validateCodeStructure(parentToValidate, request);

        // 6. Map the rest of the fields
        mapDtoToEntity(request, existing);

        return mapToResponseDTO(repository.save(existing));
    }
//  TODO: It will be possible to delete an account, but it is necessary to validate all possibilities
//    public void delete(Long id) {
//        // 1. Find existing account
//        ChartOfAccounts existing = findEntityById(id);
//
//        // 2. BLOCK: Prevent deleting accounts with children [cite: 2026-01-14]
//        boolean hasChildren = repository.existsByParent(account);
//        if (hasChildren) {
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
//                    "Cannot delete account because it has sub-accounts (children).");
//        }
//
//        // 3. TODO: Check for accounting movements [cite: 2026-01-17]
//        // if (movementRepository.existsByAccount(account)) {
//        //    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account has movements");
//        // }
//
//        // 4. TODO: Check for non-zero initial balances [cite: 2026-01-17]
//        // if (balanceRepository.hasInitialBalance(account)) {
//        //    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account has initial balances");
//        // }
//
//        repository.delete(account);
//    }




    /**
     * Read and Search operations
     */

    @Transactional(readOnly = true)
    public Page<ChartOfAccountResponseDTO> listAll(String searchTerm, Pageable pageable) {
        Company company = companyContext.getCurrentCompany();
        Page<ChartOfAccounts> entities = (searchTerm != null && !searchTerm.trim().isEmpty())
                ? repository.searchByText(company, searchTerm, pageable)
                : repository.findByCompany(company, pageable);
        return entities.map(this::mapToResponseDTO);
    }

    @Transactional(readOnly = true)
    public ChartOfAccountResponseDTO findById(Long id) {
        return mapToResponseDTO(findEntityById(id));
    }

    // Internal helper to get the Entity for internal Service use
    private ChartOfAccounts findEntityById(Long id) {
        Company company = companyContext.getCurrentCompany();
        return repository.findById(id)
                .filter(acc -> acc.getCompany().getId().equals(company.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }



    // Methods for specific UI needs (Tree view, etc.)
    @Transactional(readOnly = true)
    public List<ChartOfAccountResponseDTO> listRoots() {
        return repository.findByCompanyAndParentIsNullOrderByCodeAsc(companyContext.getCurrentCompany())
                .stream().map(this::mapToResponseDTO).toList();
    }



    @Transactional(readOnly = true)
    public ChartOfAccountResponseDTO findByCode(String code) {
        Company company = companyContext.getCurrentCompany();
        ChartOfAccounts entity = repository.findByCompanyAndCode(company, code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account code not found"));
        return mapToResponseDTO(entity);
    }

    @Transactional(readOnly = true)
    public List<ChartOfAccountResponseDTO> listChildren(Long parentId) {
        return repository.findByCompanyAndParentIdOrderByCodeAsc(companyContext.getCurrentCompany(), parentId)
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
        // Uses the paginated query from the repository
        return repository.findPostingAccounts(company, pageable)
                .map(this::mapToResponseDTO);
    }



    /**
     * Retrieves active accounts filtered by level with pagination support.
     */

    @Transactional(readOnly = true)
    public Page<ChartOfAccountResponseDTO> listByLevel(Byte level, Pageable pageable) {
        return repository.findByCompanyAndLevelAndActiveTrue(companyContext.getCurrentCompany(), level, pageable)
                .map(this::mapToResponseDTO);
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


        if (dto.getActive() != null) {
            entity.setActive(dto.getActive());
        }
    }

    public void deactivate(Long id) {
        ChartOfAccounts account = findEntityById(id);
        account.setActive(false);
    }

    public void activate(Long id) {
        ChartOfAccounts account = findEntityById(id);
        account.setActive(true);
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

        // Rule: Posting accounts must have at least 6 digits (Level 4)
        if (Boolean.TRUE.equals(request.getPostingAccount()) && codeLength < 6) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    String.format("Invalid account code: Posting accounts require at least 6 digits (Level 4 or deeper). Provided: %d digits", codeLength)
            );
        }


        // Root validation: If there is no parent, it MUST be Level 1 (1 digit)
        if (parent == null) {
            if (codeLength != 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Root accounts must have exactly 1 digit. Provided: " + code);
            }
            return;
        }


        String parentCode = parent.getCode();
        int parentLength = parentCode.length();

        // NEW: Verify code starts with parent code
        if (!code.startsWith(parentCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("Child code '%s' must start with parent code '%s'", code, parentCode));
        }

        boolean isValidJump = false;
        // PUC structure: 1 -> 2 -> 4 -> 6...
        if (parentLength == 1 && codeLength == 2) isValidJump = true;
        else if (parentLength == 2 && codeLength == 4) isValidJump = true;
        else if (parentLength >= 4 && codeLength == parentLength + 2) isValidJump = true;


        if (!isValidJump) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("Invalid code structure. Parent '%s' (%d digits) requires child to be %d digits. Provided: %d digits",
                            parentCode, parentLength, getExpectedLength(parentLength), codeLength));
        }


    }

    private int getExpectedLength(int parentLength) {
        if (parentLength == 1) return 2;
        if (parentLength == 2) return 4;
        return parentLength + 2;
    }



    private boolean isDescendant(Long accountId, ChartOfAccounts currentParent) {
        while (currentParent != null) {
            if (accountId.equals(currentParent.getId())) {
                return true;
            }
            currentParent = currentParent.getParent();
        }
        return false;
    }


    private ChartOfAccountResponseDTO mapToResponseDTO(ChartOfAccounts entity) {
        if (entity == null) {
            return null;
        }

        ChartOfAccountResponseDTO dto = new ChartOfAccountResponseDTO();

        // Standard field mapping
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setLevel(entity.getLevel());
        dto.setNature(entity.getNature());
        dto.setAccountClass(entity.getAccountClass());
        dto.setAccountType(entity.getAccountType());
        dto.setPostingAccount(entity.isPostingAccount());
        dto.setRequiresThirdParty(entity.isRequiresThirdParty());
        dto.setRequiresCostCenter(entity.isRequiresCostCenter());
        dto.setActive(entity.isActive());

        // Hierarchy mapping

        if (entity.getParent() != null) {
            dto.setParentCode(entity.getParent().getCode());
            dto.setParentName(entity.getParent().getName());
        }

        // Formatted field for the UI
        dto.setFullDescription(entity.getCode() + " - " + entity.getName());

        return dto;
    }



}