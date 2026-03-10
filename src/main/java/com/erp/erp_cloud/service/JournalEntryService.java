package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.*;
import com.erp.erp_cloud.entity.*;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.exception.ResourceNotFoundException;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.repository.CostCenterRepository;
import com.erp.erp_cloud.repository.ThirdPartyRepository;
import com.erp.erp_cloud.security.context.CompanyContext;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JournalEntryService {

    private static final Logger log = LoggerFactory.getLogger(JournalEntryService.class);
    private static final BigDecimal ROUNDING_TOLERANCE = new BigDecimal("0.01");

    private final JournalEntryRepository repository;
    private final ChartOfAccountsRepository accountRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final CostCenterRepository costCenterRepository;
    private final DocumentTypeService docTypeService;
    private final CompanyContext companyContext;
    private final AccountingEngineService accountingEngine;

    /**
     * Creates a new journal entry with automated taxes and balancing.
     */
    @Transactional
    public JournalEntryResponseDTO create(JournalEntryRequest request) {
        Company currentCompany = companyContext.getCurrentCompany();
        log.debug("Creating journal entry for company: {}", currentCompany.getId());

        // 1. Header Validation
        validateEntryDate(request.getEntryDate());
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new InvalidOperationException("Journal entry must contain at least one item.");
        }

        // 2. Document Setup
        DocumentType docType = docTypeService.findById(request.getDocumentTypeId());
        if (!docType.getCompany().getId().equals(currentCompany.getId())) {
            throw new InvalidOperationException("This Document Type does not belong to the current company.");
        }

        JournalEntry entry = new JournalEntry();
        entry.setDocumentType(docType);
        entry.setEntryDate(request.getEntryDate());
        entry.setDescription(request.getDescription());
        entry.setCompany(currentCompany);

        // Consecutive logic with pessimistic lock
        Long nextNumber = docTypeService.getNextConsecutive(docType.getId());
        entry.setConsecutive(nextNumber);
        entry.setDocumentNumber(generateDocNumber(docType, nextNumber));

        // Safety check for duplicate document numbers
        if (repository.existsByCompanyAndDocumentNumber(currentCompany, entry.getDocumentNumber())) {
            log.error("Duplicate document number detected: {}", entry.getDocumentNumber());
            throw new InvalidOperationException(
                    "Document number already exists. Please try again.");
        }

        // 3. PASS 1: Process User Items
        for (int i = 0; i < request.getItems().size(); i++) {
            var itemDto = request.getItems().get(i);
            final int itemIndex = i;
            validateItemAmounts(itemDto.getDebit(), itemDto.getCredit(), itemIndex);

            ChartOfAccounts account = fetchAndValidateAccount(itemDto.getAccountId(), currentCompany);

            JournalEntryItem item = new JournalEntryItem();
            item.setAccount(account);

            // Ensure amounts are never null
            BigDecimal debit = itemDto.getDebit() != null
                    ? itemDto.getDebit().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal credit = itemDto.getCredit() != null
                    ? itemDto.getCredit().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            item.setDebit(debit);
            item.setCredit(credit);
            item.setDescription(itemDto.getDescription());

            handleThirdParty(item, itemDto, account, currentCompany);
            handleCostCenter(item, itemDto, account, currentCompany);

            entry.addItem(item);
        }

        // 4. PASS 2: Apply System Adjustments (Taxes & Balance)
        applySystemAdjustments(entry, currentCompany);

        // 5. Final Integrity Check
        finalIntegrityCheck(entry);

        // Log if auto-balancing was applied
        if (wasAutoBalanced(entry)) {
            log.warn("Journal entry {} was auto-balanced using account {}",
                    entry.getDocumentNumber(),
                    entry.getDocumentType().getDefaultAccount() != null
                            ? entry.getDocumentType().getDefaultAccount().getCode()
                            : "N/A");
        }

        return mapToResponseDTO(repository.save(entry));
    }

    /**
     * Logic to calculate taxes and close the accounting gap.
     */
    private void applySystemAdjustments(JournalEntry entry, Company company) {
        BigDecimal runningBalance = BigDecimal.ZERO;
        List<JournalEntryItem> taxLines = new ArrayList<>();

        // Calculate balance from user items and detect taxes
        for (JournalEntryItem item : entry.getItems()) {
            BigDecimal debit = Optional.ofNullable(item.getDebit()).orElse(BigDecimal.ZERO);
            BigDecimal credit = Optional.ofNullable(item.getCredit()).orElse(BigDecimal.ZERO);
            runningBalance = runningBalance.add(debit).subtract(credit);

            // Get actual transaction amount (either debit or credit, not both)
            BigDecimal baseForTax = debit.compareTo(BigDecimal.ZERO) > 0 ? debit : credit;

            if (baseForTax.compareTo(BigDecimal.ZERO) > 0) {
                TaxCalculationResult taxCheck = accountingEngine.calculateTax(item.getAccount(), baseForTax);

                if (taxCheck.isTaxable() && taxCheck.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
                    log.debug("Tax detected: {} on account {} - Amount: {}",
                            taxCheck.getTaxName(), item.getAccount().getCode(), taxCheck.getTaxAmount());

                    JournalEntryItem taxItem = createAutoTaxItem(taxCheck, item, company);
                    taxLines.add(taxItem);

                    BigDecimal taxDebit = Optional.ofNullable(taxItem.getDebit()).orElse(BigDecimal.ZERO);
                    BigDecimal taxCredit = Optional.ofNullable(taxItem.getCredit()).orElse(BigDecimal.ZERO);
                    runningBalance = runningBalance.add(taxDebit).subtract(taxCredit);
                }
            }
        }

        // Add detected tax lines
        taxLines.forEach(entry::addItem);
        if (!taxLines.isEmpty()) {
            log.info("Added {} auto-calculated tax line(s) to entry", taxLines.size());
        }

        // Apply final balancing line if a gap exists (with tolerance for rounding)
        if (runningBalance.abs().compareTo(ROUNDING_TOLERANCE) > 0) {
            log.warn("Applying balancing adjustment of {} to document type {}",
                    runningBalance, entry.getDocumentType().getCode());
            applyBalancingLine(entry, runningBalance, entry.getDocumentType());
        }
    }

    /**
     * Creates an automatic tax line item based on tax calculation result.
     */
    private JournalEntryItem createAutoTaxItem(TaxCalculationResult tax, JournalEntryItem parent, Company company) {
        JournalEntryItem item = new JournalEntryItem();
        ChartOfAccounts taxAccount = accountRepository.findById(tax.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Tax Account", tax.getAccountId()));

        item.setAccount(taxAccount);

        // Enhanced description with tax details
        BigDecimal baseAmount = parent.getDebit().add(parent.getCredit());
        item.setDescription(String.format("Auto-tax: %s (%.2f%% on %s)",
                tax.getTaxName(),
                tax.getRate(),
                baseAmount.setScale(2, RoundingMode.HALF_UP)
        ));

        // Inherit third party from parent line
        item.setThirdParty(parent.getThirdParty());

        BigDecimal amount = tax.getTaxAmount().setScale(2, RoundingMode.HALF_UP);
        if ("D".equalsIgnoreCase(tax.getSign())) {
            item.setDebit(amount);
            item.setCredit(BigDecimal.ZERO);
        } else {
            item.setDebit(BigDecimal.ZERO);
            item.setCredit(amount);
        }
        return item;
    }

    /**
     * Adds a balancing line to close any accounting gap.
     */
    private void applyBalancingLine(JournalEntry entry, BigDecimal runningBalance, DocumentType docType) {
        if (docType.getDefaultAccount() == null) {
            throw new InvalidOperationException(
                    String.format("Document %s is unbalanced (difference: %s), but no default account is configured for auto-balancing.",
                            docType.getCode(),
                            runningBalance.setScale(2, RoundingMode.HALF_UP))
            );
        }

        JournalEntryItem balanceLine = new JournalEntryItem();
        balanceLine.setAccount(docType.getDefaultAccount());
        balanceLine.setDescription(String.format("System balance adjustment (gap: %s)",
                runningBalance.abs().setScale(2, RoundingMode.HALF_UP)));

        BigDecimal gap = runningBalance.abs().setScale(2, RoundingMode.HALF_UP);
        if (runningBalance.signum() > 0) { // More debits than credits
            balanceLine.setCredit(gap);
            balanceLine.setDebit(BigDecimal.ZERO);
        } else {
            balanceLine.setDebit(gap);
            balanceLine.setCredit(BigDecimal.ZERO);
        }
        entry.addItem(balanceLine);
    }

    /**
     * Final validation to ensure the entry is perfectly balanced.
     */
    private void finalIntegrityCheck(JournalEntry entry) {
        BigDecimal total = entry.getItems().stream()
                .map(i -> Optional.ofNullable(i.getDebit()).orElse(BigDecimal.ZERO)
                        .subtract(Optional.ofNullable(i.getCredit()).orElse(BigDecimal.ZERO)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.abs().setScale(2, RoundingMode.HALF_UP).compareTo(ROUNDING_TOLERANCE) > 0) {
            throw new InvalidOperationException(
                    String.format("Final document is unbalanced. Difference: %s",
                            total.setScale(2, RoundingMode.HALF_UP))
            );
        }
    }

    /**
     * Checks if the entry was auto-balanced.
     */
    private boolean wasAutoBalanced(JournalEntry entry) {
        return entry.getItems().stream()
                .anyMatch(item -> item.getDescription() != null &&
                        item.getDescription().startsWith("System balance adjustment"));
    }

    /**
     * Generates document number from document type and consecutive.
     */
    private String generateDocNumber(DocumentType docType, Long consecutive) {
        return (docType.getPrefix() != null && !docType.getPrefix().isBlank())
                ? docType.getPrefix().trim() + "-" + consecutive
                : consecutive.toString();
    }

    /**
     * Fetches and validates an account for use in journal entries.
     */
    private ChartOfAccounts fetchAndValidateAccount(Long accountId, Company company) {
        ChartOfAccounts account = accountRepository.findById(accountId)
                .filter(a -> a.getCompany().getId().equals(company.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

        if (!account.isActive()) {
            throw new InvalidOperationException(
                    String.format("Account %s - %s is inactive and cannot be used.",
                            account.getCode(), account.getName())
            );
        }

        if (!account.isPostingAccount()) {
            throw new InvalidOperationException(
                    String.format("Account %s - %s is not a posting account.",
                            account.getCode(), account.getName())
            );
        }

        return account;
    }

    // --- HELPER VALIDATIONS ---

    /**
     * Validates entry date constraints.
     */
    private void validateEntryDate(LocalDate date) {
        if (date == null) {
            throw new InvalidOperationException("Entry date is required");
        }

        LocalDate today = LocalDate.now();

        if (date.isAfter(today)) {
            throw new InvalidOperationException("Entry date cannot be in the future");
        }

        LocalDate minAllowedDate = today.minusDays(90);
        if (date.isBefore(minAllowedDate)) {
            throw new InvalidOperationException(
                    String.format("Entry date cannot be older than %s (90 days)", minAllowedDate)
            );
        }

        // TODO: Add fiscal period validation when implemented
        // if (date.isBefore(company.getCurrentFiscalPeriodStart())) {
        //     throw new InvalidOperationException(
        //         "Cannot create entries before current fiscal period start date");
        // }
    }

    /**
     * Validates that each item has either debit OR credit, but not both or neither.
     */
    private void validateItemAmounts(BigDecimal debit, BigDecimal credit, int itemIndex) {
        boolean hasDebit = debit != null && debit.compareTo(BigDecimal.ZERO) > 0;
        boolean hasCredit = credit != null && credit.compareTo(BigDecimal.ZERO) > 0;

        if (hasDebit && hasCredit) {
            throw new InvalidOperationException(
                    String.format("Item #%d cannot have both debit (%.2f) and credit (%.2f).",
                            itemIndex + 1, debit, credit)
            );
        }

        if (!hasDebit && !hasCredit) {
            throw new InvalidOperationException(
                    String.format("Item #%d must have either debit or credit greater than zero.",
                            itemIndex + 1)
            );
        }
    }

    /**
     * Handles third party validation and assignment.
     */
    private void handleThirdParty(JournalEntryItem item, JournalEntryRequest.ItemRequest dto,
                                  ChartOfAccounts account, Company company) {
        if (account.isRequiresThirdParty()) {
            if (dto.getThirdPartyId() == null) {
                throw new InvalidOperationException(
                        String.format("Account %s requires a Third Party", account.getCode())
                );
            }

            ThirdParty thirdParty = thirdPartyRepository.findById(dto.getThirdPartyId())
                    .filter(t -> t.getCompany().getId().equals(company.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("ThirdParty", dto.getThirdPartyId()));

            item.setThirdParty(thirdParty);
        }
    }

    /**
     * Handles cost center validation and assignment.
     */
    private void handleCostCenter(JournalEntryItem item, JournalEntryRequest.ItemRequest dto,
                                  ChartOfAccounts account, Company company) {
        if (account.isRequiresCostCenter()) {
            if (dto.getCostCenterId() == null) {
                throw new InvalidOperationException(
                        String.format("Account %s requires a Cost Center", account.getCode())
                );
            }

            CostCenter costCenter = costCenterRepository.findById(dto.getCostCenterId())
                    .filter(c -> c.getCompany().getId().equals(company.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("CostCenter", dto.getCostCenterId()));

            item.setCostCenter(costCenter);
        }
    }

    // --- READ METHODS ---

    /**
     * Lists journal entries with optional filtering and pagination.
     */
    public Page<JournalEntryResponseDTO> listEntries(String searchTerm, LocalDate start,
                                                     LocalDate end, Pageable pageable) {
        Company company = companyContext.getCurrentCompany();

        log.debug("Listing journal entries for company: {} with filters - search: {}, dates: {} to {}",
                company.getId(), searchTerm, start, end);

        return repository.searchEntries(company, searchTerm, start, end, pageable)
                .map(this::mapToResponseDTO);
    }

    /**
     * Finds a journal entry by document number.
     */
    public JournalEntryResponseDTO findByDocumentNumber(String docNum) {
        Company company = companyContext.getCurrentCompany();

        return repository.findByCompanyAndDocumentNumber(company, docNum)
                .map(this::mapToResponseDTO)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Journal entry with document number '" + docNum + "' not found"
                ));
    }

    /**
     * Generates a trial balance report with account class summaries.
     * Optimized for single-pass processing.
     */
    @Transactional(readOnly = true)
    public TrialBalanceReport getTrialBalanceReport() {
        Company company = companyContext.getCurrentCompany();
        List<TrialBalanceLine> lines = accountRepository.getTrialBalance(company);

        // Single-pass calculation for better performance
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        Map<String, BigDecimal> summary = new HashMap<>();
        int invalidCount = 0;

        for (TrialBalanceLine line : lines) {
            totalDebit = totalDebit.add(line.getTotalDebit());
            totalCredit = totalCredit.add(line.getTotalCredit());

            if (line.getNetBalance() != null) {
                String className = getAccountClassName(line.getAccountCode());
                summary.merge(className, line.getNetBalance(), BigDecimal::add);
            } else {
                invalidCount++;
            }
        }

        // Log data quality issues
        if (invalidCount > 0) {
            log.warn("Found {} trial balance lines with null net balance", invalidCount);
        }

        boolean isBalanced = totalDebit.setScale(2, RoundingMode.HALF_UP)
                .compareTo(totalCredit.setScale(2, RoundingMode.HALF_UP)) == 0;

        return new TrialBalanceReport(lines, totalDebit, totalCredit, isBalanced, summary);
    }

    /**
     * Helper to translate the first digit of the code into a Category Name.
     * Based on Colombian PUC (Plan Único de Cuentas) structure.
     */
    private String getAccountClassName(String code) {
        if (code == null || code.isEmpty()) {
            return "Other";
        }

        char firstDigit = code.charAt(0);
        return switch (firstDigit) {
            case '1' -> "1 - Assets";
            case '2' -> "2 - Liabilities";
            case '3' -> "3 - Equity";
            case '4' -> "4 - Income";
            case '5' -> "5 - Expenses";
            case '6', '7' -> "6/7 - Costs";
            default -> "Other";
        };
    }

    /**
     * Maps JournalEntry entity to response DTO.
     */
    private JournalEntryResponseDTO mapToResponseDTO(JournalEntry entry) {
        if (entry == null) {
            return null;
        }

        JournalEntryResponseDTO dto = new JournalEntryResponseDTO();
        dto.setId(entry.getId());
        dto.setDocumentNumber(entry.getDocumentNumber());
        dto.setEntryDate(entry.getEntryDate());
        dto.setDescription(entry.getDescription());

        List<JournalEntryResponseDTO.ItemResponse> items = entry.getItems().stream()
                .map(item -> {
                    JournalEntryResponseDTO.ItemResponse i = new JournalEntryResponseDTO.ItemResponse();
                    i.setId(item.getId());
                    i.setAccountCode(item.getAccount().getCode());
                    i.setAccountName(item.getAccount().getName());
                    i.setDebit(item.getDebit());
                    i.setCredit(item.getCredit());
                    i.setDescription(item.getDescription());

                    if (item.getThirdParty() != null) {
                        i.setThirdPartyIdNumber(item.getThirdParty().getDocumentNumber());
                        i.setThirdPartyName(item.getThirdParty().getLegalDisplayName());
                    }

                    if (item.getCostCenter() != null) {
                        i.setCostCenterName(item.getCostCenter().getName());
                    }

                    return i;
                })
                .toList();

        dto.setItems(items);
        return dto;
    }
}