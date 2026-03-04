package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.JournalEntryRequest;
import com.erp.erp_cloud.dto.JournalEntryResponseDTO;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.JournalEntry;
import com.erp.erp_cloud.entity.JournalEntryItem;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.exception.ResourceNotFoundException;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.repository.CostCenterRepository;
import com.erp.erp_cloud.repository.ThirdPartyRepository;
import com.erp.erp_cloud.security.context.CompanyContext;
import com.erp.erp_cloud.entity.Company;

import java.math.RoundingMode;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JournalEntryService {

    private static final Logger log = LoggerFactory.getLogger(JournalEntryService.class);

    private final JournalEntryRepository repository;
    private final ChartOfAccountsRepository accountRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final CostCenterRepository costCenterRepository;
    private final DocumentTypeService docTypeService;
    private final CompanyContext companyContext;
    private final AccountingEngineService accountingEngine;

    /**
     * Creates a new accounting voucher with full validation.
     */
    @Transactional
    public JournalEntryResponseDTO create(JournalEntryRequest request) {
        Company currentCompany = companyContext.getCurrentCompany();

        log.debug("Creating journal entry for company: {}", currentCompany.getId());

        // 1. Validate entry date
        validateEntryDate(request.getEntryDate());

        // 2. Shallow Validation (Items list)
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new InvalidOperationException("Journal entry must contain at least one item.");
        }

        // 3. Double-Entry Balance Validation
        validateAccountingBalance(request.getItems());

        // 4. PRE-VALIDATE all items (fail fast before creating entities)
        for (int i = 0; i < request.getItems().size(); i++) {
            var itemDto = request.getItems().get(i);
            final int itemIndex = i;

            validateItemAmounts(itemDto.getDebit(), itemDto.getCredit(), itemIndex);

            // Verify account exists and is valid
            var account = accountRepository.findById(itemDto.getAccountId())
                    .filter(a -> a.getCompany().getId().equals(currentCompany.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            String.format("Account not found for item #%d", itemIndex + 1),
                            itemDto.getAccountId())
                    );

            validateAccountState(account);
        }

        // 5. Document Type & Consecutive Handling
        var docType = docTypeService.findById(request.getDocumentTypeId());
        if (!docType.getCompany().getId().equals(currentCompany.getId())) {
            throw new InvalidOperationException("This Document Type does not belong to the tenant company.");
        }

        JournalEntry entry = new JournalEntry();
        entry.setDocumentType(docType);
        entry.setEntryDate(request.getEntryDate());
        entry.setDescription(request.getDescription());
        entry.setCompany(currentCompany);

        // Assign consecutive logic
        Long nextNumber = docTypeService.getNextConsecutive(docType.getId());
        entry.setConsecutive(nextNumber);

        // Build the unique string: Prefix + Number (e.g., "FV-1")
        if (docType.getPrefix() != null && !docType.getPrefix().isBlank()) {
            entry.setDocumentNumber(docType.getPrefix().trim() + "-" + nextNumber);
        } else {
            entry.setDocumentNumber(nextNumber.toString());
        }

        // Safety check for duplicate document numbers
        if (repository.existsByCompanyAndDocumentNumber(currentCompany, entry.getDocumentNumber())) {
            log.error("Duplicate document number detected: {}", entry.getDocumentNumber());
            throw new InvalidOperationException(
                    "Document number already exists. Please try again."
            );
        }

        // 6. Map and Validate Items
        for (var itemDto : request.getItems()) {
            // Account Retrieval (already validated above, so we can safely get it)
            var account = accountRepository.findById(itemDto.getAccountId())
                    .filter(a -> a.getCompany().getId().equals(currentCompany.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("ChartOfAccount", itemDto.getAccountId()));

            JournalEntryItem item = new JournalEntryItem();
            item.setAccount(account);

            // --- INTEGRATION: ACCOUNTING ENGINE (TAX AUTO-CHECK) ---
            // Get the actual amount (either debit or credit, already validated that only one exists)
            BigDecimal baseAmount = itemDto.getDebit() != null ? itemDto.getDebit() : itemDto.getCredit();

            if (baseAmount != null && baseAmount.compareTo(BigDecimal.ZERO) > 0) {
                var taxCheck = accountingEngine.calculateTax(account, baseAmount);
                if (taxCheck.isTaxable()) {
                    log.info("Tax detected for account {}: {} at rate {}%",
                            account.getCode(), taxCheck.getTaxName(), taxCheck.getRate());
                    // Note: In advanced scenarios, we could automatically inject a NEW item here.
                }
            }

            // Standard Third Party & Cost Center Logic
            handleThirdParty(item, itemDto, account, currentCompany);
            handleCostCenter(item, itemDto, account, currentCompany);

            // Set amounts with proper scale
            BigDecimal debit = itemDto.getDebit() != null
                    ? itemDto.getDebit().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal credit = itemDto.getCredit() != null
                    ? itemDto.getCredit().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            item.setDebit(debit);
            item.setCredit(credit);
            item.setDescription(itemDto.getDescription());

            entry.addItem(item);
        }

        return mapToResponseDTO(repository.save(entry));
    }

    /**
     * Validates entry date to prevent future dates and optionally backdated entries
     */
    private void validateEntryDate(LocalDate entryDate) {
        if (entryDate == null) {
            throw new InvalidOperationException("Entry date is required");
        }

        LocalDate today = LocalDate.now();

        // Prevent future dates
        if (entryDate.isAfter(today)) {
            throw new InvalidOperationException("Entry date cannot be in the future");
        }

        // Optional: Prevent entries too far in the past (e.g., 90 days)
        LocalDate minAllowedDate = today.minusDays(90);
        if (entryDate.isBefore(minAllowedDate)) {
            throw new InvalidOperationException(
                    String.format("Entry date cannot be older than %s (90 days)", minAllowedDate)
            );
        }

        // TODO: Add fiscal period validation when implemented
        // Company company = companyContext.getCurrentCompany();
        // if (entryDate.isBefore(company.getCurrentFiscalPeriodStart())) {
        //     throw new InvalidOperationException(
        //         "Cannot create entries before current fiscal period start date"
        //     );
        // }
    }

    private void validateAccountState(ChartOfAccounts account) {
        if (!account.isActive()) {
            throw new InvalidOperationException(
                    String.format("Account %s - %s is inactive and cannot be used",
                            account.getCode(), account.getName())
            );
        }
        if (!account.isPostingAccount()) {
            throw new InvalidOperationException(
                    String.format("Account %s - %s is not a posting account",
                            account.getCode(), account.getName())
            );
        }
    }

    private void handleThirdParty(JournalEntryItem item, JournalEntryRequest.ItemRequest dto,
                                  ChartOfAccounts account, Company company) {
        if (account.isRequiresThirdParty()) {
            if (dto.getThirdPartyId() == null) {
                throw new InvalidOperationException(
                        String.format("Account %s requires a Third Party", account.getCode())
                );
            }
            var tp = thirdPartyRepository.findById(dto.getThirdPartyId())
                    .filter(t -> t.getCompany().getId().equals(company.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("ThirdParty", dto.getThirdPartyId()));
            item.setThirdParty(tp);
        }
    }

    private void handleCostCenter(JournalEntryItem item, JournalEntryRequest.ItemRequest dto,
                                  ChartOfAccounts account, Company company) {
        if (account.isRequiresCostCenter()) {
            if (dto.getCostCenterId() == null) {
                throw new InvalidOperationException(
                        String.format("Account %s requires a Cost Center", account.getCode())
                );
            }
            var cc = costCenterRepository.findById(dto.getCostCenterId())
                    .filter(c -> c.getCompany().getId().equals(company.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("CostCenter", dto.getCostCenterId()));
            item.setCostCenter(cc);
        }
    }

    /**
     * Validates that total debits equal total credits (Double-Entry Accounting Rule)
     */
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
            log.warn("Unbalanced transaction detected. Debit: {}, Credit: {}", totalDebit, totalCredit);
            throw new InvalidOperationException(
                    String.format("Unbalanced transaction. Total Debit: %s, Total Credit: %s",
                            totalDebit, totalCredit)
            );
        }
    }

    /**
     * Validates that each item has either debit OR credit, but not both or neither
     */
    private void validateItemAmounts(BigDecimal debit, BigDecimal credit, int itemIndex) {
        BigDecimal safeDebit = Optional.ofNullable(debit).orElse(BigDecimal.ZERO);
        BigDecimal safeCredit = Optional.ofNullable(credit).orElse(BigDecimal.ZERO);

        boolean hasDebit = safeDebit.compareTo(BigDecimal.ZERO) > 0;
        boolean hasCredit = safeCredit.compareTo(BigDecimal.ZERO) > 0;

        if (hasDebit && hasCredit) {
            throw new InvalidOperationException(
                    String.format("Item #%d cannot have both debit (%.2f) and credit (%.2f)",
                            itemIndex + 1, safeDebit, safeCredit)
            );
        }
        if (!hasDebit && !hasCredit) {
            throw new InvalidOperationException(
                    String.format("Item #%d must have either debit or credit greater than zero",
                            itemIndex + 1)
            );
        }
    }

    /**
     * Lists journal entries with optional filtering and pagination
     */
    public Page<JournalEntryResponseDTO> listEntries(String searchTerm, LocalDate startDate,
                                                     LocalDate endDate, Pageable pageable) {
        Company company = companyContext.getCurrentCompany();

        log.debug("Listing journal entries for company: {} with filters - search: {}, dates: {} to {}",
                company.getId(), searchTerm, startDate, endDate);

        return repository.searchEntries(company, searchTerm, startDate, endDate, pageable)
                .map(this::mapToResponseDTO);
    }

    /**
     * Find journal entry by document number
     */
    public JournalEntryResponseDTO findByDocumentNumber(String documentNumber) {
        Company company = companyContext.getCurrentCompany();

        JournalEntry entry = repository.findByCompanyAndDocumentNumber(company, documentNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Journal entry with document number '" + documentNumber + "' not found"
                ));

        return mapToResponseDTO(entry);
    }

    /**
     * Maps JournalEntry entity to response DTO
     */
    private JournalEntryResponseDTO mapToResponseDTO(JournalEntry entry) {
        if (entry == null) {
            return null;
        }

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
}