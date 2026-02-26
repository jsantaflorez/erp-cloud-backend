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

import java.math.RoundingMode;
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
@Transactional(readOnly = true) // Best practice: Read-only by default
public class JournalEntryService {

    private final JournalEntryRepository repository;
    private final ChartOfAccountsRepository accountRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final CostCenterRepository costCenterRepository;
    private final DocumentTypeService docTypeService;
    private final CompanyContext companyContext;
    /**
     * Creates a new accounting voucher with full validation.
     */

    @Transactional
    public JournalEntryResponseDTO create(JournalEntryRequest request) {
        Company currentCompany = companyContext.getCurrentCompany();
        // 1. Shallow Validation (Items list and amounts)
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Journal entry must contain at least one item.");
        }

        // 2. Double-Entry Balance Validation
        validateAccountingBalance(request.getItems());

        // 3. Document Type & Consecutive Handling
        var docType = docTypeService.findById(request.getDocumentTypeId());
        if (!docType.getCompany().getId().equals(companyContext.getCurrentCompany().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This Document Type does not belong to the tenant company.");
        }
        JournalEntry entry = new JournalEntry();
        entry.setDocumentType(docType);
        entry.setEntryDate(request.getEntryDate());
        entry.setDescription(request.getDescription());
        entry.setCompany(companyContext.getCurrentCompany());

        // Assign consecutive logic
        Long nextNumber = docTypeService.getNextConsecutive(docType.getId());
        entry.setConsecutive(nextNumber);
        // Build the unique string: Prefix + Number (e.g., "FV-1")
        entry.setDocumentNumber(docType.getPrefix() + "-" + nextNumber);


        // 4. Map and Validate Items
        for (var itemDto : request.getItems()) {
            // Validation of Debit vs Credit (Using safeDebit logic)
            validateItemAmounts(itemDto.getDebit(), itemDto.getCredit());

            JournalEntryItem item = new JournalEntryItem();
        // 5. Account Validation
            var account = accountRepository.findById(itemDto.getAccountId())
                    .filter(a -> a.getCompany().getId().equals(companyContext.getCurrentCompany().getId())) // SECURITY
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Account not found or access denied: " + itemDto.getAccountId()));

            if (!account.isActive()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Account " + account.getCode() + " is inactive and cannot receive new entries.");
            }


            if (!account.isPostingAccount()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Account " + account.getCode() + " is not a posting account.");
            }
            // 6. Third Party Validation
            if (account.isRequiresThirdParty()) {
                    if (itemDto.getThirdPartyId() == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account " + account.getCode() + " requires a Third Party.");
                    }
                var thirdParty = thirdPartyRepository.findById(itemDto.getThirdPartyId())
                        .filter(tp -> tp.getCompany().getId().equals(currentCompany.getId()))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Third Party not found."));


                if (thirdParty.getActive() != null && !thirdParty.getActive()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Third Party " + thirdParty.getLegalDisplayName() + " is inactive.");
                }
                item.setThirdParty(thirdParty);



            }

            // 7. Cost Center Validation
            if (account.isRequiresCostCenter()) {
                if (itemDto.getCostCenterId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account " + account.getCode() + " requires a Cost Center.");
                }

                var costCenter = costCenterRepository.findById(itemDto.getCostCenterId())
                        .filter(cc -> cc.getCompany().getId().equals(currentCompany.getId()))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cost Center not found."));

                if (!costCenter.isActive()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cost Center '" + costCenter.getName() + "' is inactive.");
                }

                if (!costCenter.isAllowsMovement()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cost Center '" + costCenter.getName() + "' does not allow movements.");
                }
                item.setCostCenter(costCenter);
            }

            item.setAccount(account);
            item.setDebit(Optional.ofNullable(itemDto.getDebit()).orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            item.setCredit(Optional.ofNullable(itemDto.getCredit()).orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            item.setDescription(itemDto.getDescription());

            entry.addItem(item);
        }



        JournalEntry savedEntry = repository.save(entry);
        return mapToResponseDTO(savedEntry);
    }


    private void validateAccountingBalance(List<JournalEntryRequest.ItemRequest> items) {
        BigDecimal totalDebit = items.stream()
                .map(i -> Optional.ofNullable(i.getDebit()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalCredit = items.stream()
                .map(i -> Optional.ofNullable(i.getCredit()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("Unbalanced transaction. Total Debit: %s, Total Credit: %s", totalDebit, totalCredit));
        }
    }

    private void validateItemAmounts(BigDecimal debit, BigDecimal credit) {
        BigDecimal safeDebit = Optional.ofNullable(debit).orElse(BigDecimal.ZERO);
        BigDecimal safeCredit = Optional.ofNullable(credit).orElse(BigDecimal.ZERO);

        boolean hasDebit = safeDebit.compareTo(BigDecimal.ZERO) > 0;
        boolean hasCredit = safeCredit.compareTo(BigDecimal.ZERO) > 0;

        if (hasDebit && hasCredit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An item cannot have both a debit and a credit.");
        }
        if (!hasDebit && !hasCredit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An item must have a value greater than zero.");
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


     public Page<JournalEntryResponseDTO> listEntries(String searchTerm, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return repository.searchEntries(companyContext.getCurrentCompany(), searchTerm, startDate, endDate, pageable)
                .map(this::mapToResponseDTO);
    }


}

