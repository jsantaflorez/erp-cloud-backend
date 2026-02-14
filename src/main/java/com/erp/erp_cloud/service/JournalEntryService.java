package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.JournalEntryRequest;
import com.erp.erp_cloud.dto.JournalEntryResponseDTO;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.JournalEntry;
import com.erp.erp_cloud.entity.JournalEntryItem;

import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.repository.CostCenterRepository;
import com.erp.erp_cloud.repository.ThirdPartyRepository;
import com.erp.erp_cloud.security.context.CompanyContext;
import com.erp.erp_cloud.entity.Company;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class JournalEntryService {

    private final JournalEntryRepository repository;
    private final ChartOfAccountsRepository accountRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final CostCenterRepository costCenterRepository;
    private final DocumentTypeService docTypeService;
    private final CompanyContext companyContext;
    // We will need these for cross-validation later:
    // private final ChartOfAccountsService accountService;
    // private final ThirdPartyService thirdPartyService;

    @Transactional

    public JournalEntryResponseDTO create(JournalEntryRequest request) {

        // 1. Validate Items List Exists

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Journal entry must contain at least one item.");
        }

        // 2. Validate Double-Entry Accounting (Balance)
        validateAccountingBalance(request.getItems());

        // 3. Load DocumentType and assign consecutive (Separation of responsibilities)>


        var docType = docTypeService.findById(request.getDocumentTypeId());
        if (!docType.getCompany().getId().equals(companyContext.getCurrentCompany().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This Document Type does not belong to the tenant company.");
        }
        JournalEntry entry = new JournalEntry();
        entry.setDocumentType(docType);
        entry.setEntryDate(request.getEntryDate());
        entry.setDescription(request.getDescription());
        entry.setCompany(companyContext.getCurrentCompany());
// Assign the number using the service method
        Long nextNumber = docTypeService.getNextConsecutive(docType.getId());
        entry.setConsecutive(nextNumber);
// Build the unique string: Prefix + Number (e.g., "FV-1")
        String formattedNumber = docType.getPrefix() + "-" + nextNumber;
        entry.setDocumentNumber(formattedNumber);


        // 4. Map and Validate Items
        for (var itemDto : request.getItems()) {
            // Validation of Debit vs Credit (Using safeDebit logic)
            validateItemAmounts(itemDto.getDebit(), itemDto.getCredit());

            JournalEntryItem item = new JournalEntryItem();
       // 5. Fetch the Account
            var account = accountRepository.findById(itemDto.getAccountId())
                    .filter(a -> a.getCompany().getId().equals(companyContext.getCurrentCompany().getId())) // SECURITY
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Account not found or access denied: " + itemDto.getAccountId()));

            // --- THE ACTIVE GUARD (ACCOUNT) ---
            if (!account.isActive()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Account " + account.getCode() + " is inactive and cannot receive new entries.");
            }



        // 6. Check if it's a posting account
            if (!account.isPostingAccount()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Account " + account.getCode() + " is not a posting account.");
            }
        // 7. Validate Third Party Requirement
            if (account.isRequiresThirdParty()) {
                if (itemDto.getThirdPartyId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Account " + account.getCode() + " requires a Third Party.");
                }

                var thirdParty = thirdPartyRepository.findById(itemDto.getThirdPartyId())
                        .filter(tp -> tp.getCompany().getId().equals(companyContext.getCurrentCompany().getId()))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Third Party not found or access denied."));

                // TODO: Add ThirdParty active check here once implemented

                item.setThirdParty(thirdParty);

            }

        // 8. Validate Cost Center Requirement
            if (account.isRequiresCostCenter()) {
                if (itemDto.getCostCenterId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Account " + account.getCode() + " requires a Cost Center.");
                }
                var costCenter = costCenterRepository.findById(itemDto.getCostCenterId())
                        // SECURITY: Ensure the Cost Center belongs to the active company
                        .filter(cc -> cc.getCompany().getId().equals(companyContext.getCurrentCompany().getId()))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Cost Center not found or access denied."));
            // --- THE ACTIVE GUARD (COST CENTER) ---
                if (!costCenter.isActive()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "The Cost Center '" + costCenter.getName() + "' is inactive.");
                }


            // VALIDATION: Check "allows_movement" flag
                if (!costCenter.isAllowsMovement()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "The Cost Center '" + costCenter.getName() + "' is a parent category and does not allow movements.");
                }

                item.setCostCenter(costCenter);
            }
            item.setAccount(account);
            item.setDebit(Optional.ofNullable(itemDto.getDebit()).orElse(BigDecimal.ZERO));
            item.setCredit(Optional.ofNullable(itemDto.getCredit()).orElse(BigDecimal.ZERO));
            item.setDescription(itemDto.getDescription());

            entry.addItem(item);
        }


        JournalEntry savedEntry = repository.save(entry);
        return mapToResponseDTO(savedEntry);
    }




    private void validateAccountingBalance(List<JournalEntryRequest.ItemRequest> items) {
        BigDecimal totalDebit = items.stream()
                .map(i -> Optional.ofNullable(i.getDebit()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredit = items.stream()
                .map(i -> Optional.ofNullable(i.getCredit()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The transaction is unbalanced. Total Debit: " + totalDebit + ", Total Credit: " + totalCredit);
        }
    }

    private void validateItemAmounts(BigDecimal debit, BigDecimal credit) {
       // this will avoid a NullPointerException if the client sends null:

        BigDecimal safeDebit = Optional.ofNullable(debit).orElse(BigDecimal.ZERO);
        BigDecimal safeCredit = Optional.ofNullable(credit).orElse(BigDecimal.ZERO);

        boolean hasDebit = safeDebit.compareTo(BigDecimal.ZERO) > 0;
        boolean hasCredit = safeCredit.compareTo(BigDecimal.ZERO) > 0;

        if (hasDebit && hasCredit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An item cannot have both a debit and a credit.");
        }
        if (!hasDebit && !hasCredit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An item must have either a debit or a credit greater than zero.");
        }
    }
    private JournalEntryResponseDTO mapToResponseDTO(JournalEntry entry) {
        JournalEntryResponseDTO response = new JournalEntryResponseDTO();
        response.setId(entry.getId());
        response.setDocumentNumber(entry.getDocumentNumber());
        response.setEntryDate(entry.getEntryDate());
        response.setDescription(entry.getDescription());

        List<JournalEntryResponseDTO.ItemResponse> itemDtos = entry.getItems().stream().map(item -> {
            JournalEntryResponseDTO.ItemResponse itemDto = new JournalEntryResponseDTO.ItemResponse();
            itemDto.setId(item.getId());
            itemDto.setAccountCode(item.getAccount().getCode());
            itemDto.setAccountName(item.getAccount().getName());
            itemDto.setDebit(item.getDebit());
            itemDto.setCredit(item.getCredit());



            if (item.getThirdParty() != null) {
                // Using legal name logic + document number
                itemDto.setThirdPartyIdNumber(item.getThirdParty().getDocumentNumber());
                itemDto.setThirdPartyName(item.getThirdParty().getLegalDisplayName());
            }

            if (item.getCostCenter() != null) {
                itemDto.setCostCenterName(item.getCostCenter().getName());
            }

            return itemDto;
        }).toList();

        response.setItems(itemDtos);
        return response;
    }


    @Transactional(readOnly = true)
    public Page<JournalEntryResponseDTO> listEntries(
            String searchTerm,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable) {

        Company company = companyContext.getCurrentCompany();

        // 1. Fetch filtered entities from DB
        Page<JournalEntry> entries = repository.searchEntries(
                company, searchTerm, startDate, endDate, pageable);

        // 2. Transform the page of Entities into a page of DTOs
        return entries.map(this::mapToResponseDTO);
    }
}

