package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.JournalEntryRequest;
import com.erp.erp_cloud.entity.JournalEntry;
import com.erp.erp_cloud.entity.JournalEntryItem;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.repository.CostCenterRepository;
import com.erp.erp_cloud.repository.ThirdPartyRepository;
import com.erp.erp_cloud.security.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
    public JournalEntry create(JournalEntryRequest request) {

        // 1. Validate Items List Exists

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Journal entry must contain at least one item.");
        }

        // 2. Validate Double-Entry Accounting (Balance)
        validateAccountingBalance(request.getItems());

        // 3. Load DocumentType and assign consecutive (Separation of responsibilities)>

        var docType = docTypeService.findById(request.getDocumentTypeId());
        JournalEntry entry = new JournalEntry();
        entry.setDocumentType(docType);
        entry.setEntryDate(request.getEntryDate());
        entry.setDescription(request.getDescription());
        entry.setCompany(companyContext.getCurrentCompany());

        // Assign the number using the service method
        entry.setConsecutive(docTypeService.getNextConsecutive(docType.getId()));





        // 4. Map and Validate Items
        for (var itemDto : request.getItems()) {
            // Validation of Debit vs Credit (Using safeDebit logic)
            validateItemAmounts(itemDto.getDebit(), itemDto.getCredit());

            JournalEntryItem item = new JournalEntryItem();


            // 1. Fetch the Account (Critical)


            var account = accountRepository.findById(itemDto.getAccountId())
                    .filter(a -> a.getCompany().getId().equals(companyContext.getCurrentCompany().getId())) // SECURITY
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Account not found or access denied: " + itemDto.getAccountId()));



            // 3. Check if it's a posting account (Your field name)
            if (!account.isPostingAccount()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Account " + account.getCode() + " is not a posting account.");
            }

            // 4. Validate Third Party Requirement
            if (account.isRequiresThirdParty()) {
                if (itemDto.getThirdPartyId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Account " + account.getCode() + " requires a Third Party.");
                }
                var thirdParty = thirdPartyRepository.findById(itemDto.getThirdPartyId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Third Party not found."));
                item.setThirdParty(thirdParty);
            }

            // 5. Validate Cost Center Requirement
            if (account.isRequiresCostCenter()) {
                if (itemDto.getCostCenterId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Account " + account.getCode() + " requires a Cost Center.");
                }

                var costCenter = costCenterRepository.findById(itemDto.getCostCenterId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cost Center not found."));

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

            // TODO: Map IDs to entities (Account, ThirdParty, etc.)
            entry.addItem(item);
        }


        return repository.save(entry);
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
}

