package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.*;
import com.erp.erp_cloud.dto.reports.financial.TrialBalanceLineDetailed;
import com.erp.erp_cloud.dto.reports.financial.TrialBalanceReportDetailed;
import com.erp.erp_cloud.entity.*;
import com.erp.erp_cloud.enums.AccountClass;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.exception.ResourceNotFoundException;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.repository.CostCenterRepository;
import com.erp.erp_cloud.repository.ThirdPartyRepository;
import com.erp.erp_cloud.security.context.TenantContext;
import com.erp.erp_cloud.dto.TrialBalanceLine;
import com.erp.erp_cloud.dto.reports.financial.TrialBalanceReport;

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
import java.util.*;

/**
 * Service for managing Journal Entries (Comprobantes Contables).
 *
 * Current Version: Manual Entry Only
 * - No automatic tax calculation
 * - No automatic balancing
 * - Full accountant control over all entries
 *
 * Future versions will support automated tax calculation and system adjustments.
 */
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
    private final TenantContext companyContext;
    private final AccountingPeriodService accountingPeriodService;

    // ═══════════════════════════════════════════════════════════
    // CREATE OPERATIONS
    // ═══════════════════════════════════════════════════════════

    /**
     * Creates a new journal entry with strict manual validation.
     *
     * Business Rules (Version 1.0 - Manual Entry):
     * - Entry MUST be perfectly balanced (debits = credits)
     * - NO automatic tax calculation (accountant enters taxes manually)
     * - NO automatic balancing adjustments (full control to user)
     * - Accounting period must be open for the entry date
     * - All accounts must be active and posting accounts
     * - Third party and cost center are required when account configuration demands it
     *
     * Validation Flow:
     * 1. Date validation (not in future, within 90 days, period is open)
     * 2. Balance validation (total debits must equal total credits)
     * 3. Document type validation and consecutive assignment
     * 4. Line item validation (account, amounts, third party, cost center)
     * 5. Save transaction
     *
     * Future Enhancement: Automatic tax calculation will be added in a later version
     * when the system is more refined and tax rules are fully configured.
     *
     * @param request Journal entry data from the user
     * @return Saved journal entry with generated document number
     * @throws InvalidOperationException if entry is unbalanced, period is closed,
     *         or validation fails
     * @throws ResourceNotFoundException if referenced entities don't exist
     */
    @Transactional
    public JournalEntryResponseDTO create(JournalEntryRequest request) {
        Company currentCompany = companyContext.getCurrentCompany();

        log.info("Creating journal entry for company: {}", currentCompany.getLegalName());

        // 1. Date and Period Validation
        validateEntryDate(request.getEntryDate());
        accountingPeriodService.validateDateIsOpen(request.getEntryDate(), currentCompany);

        // 2. Items Existence Validation
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new InvalidOperationException("Journal entry must contain at least one item.");
        }

        // 3. CRITICAL: Balance Validation (Zero Tolerance)
        validateBalance(request);

        // 4. Document Type Setup and Validation
        DocumentType docType = validateAndGetDocumentType(request.getDocumentTypeId(), currentCompany);

        // 5. Create Entry Header
        JournalEntry entry = createEntryHeader(docType, request, currentCompany);

        // 6. Process Line Items
        processLineItems(entry, request, currentCompany);

        // 7. Save and Return
        log.info("Saving journal entry {} with {} items for company {}",
                entry.getDocumentNumber(), entry.getItems().size(), currentCompany.getLegalName());

        return mapToResponseDTO(repository.save(entry));
    }


    

    // ═══════════════════════════════════════════════════════════
    // PRIVATE CREATION HELPERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Validates that total debits equal total credits.
     * This is a fundamental accounting principle - the entry must balance.
     */
    private void validateBalance(JournalEntryRequest request) {
        BigDecimal totalDebit = request.getItems().stream()
                .map(i -> i.getDebit() != null ? i.getDebit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredit = request.getItems().stream()
                .map(i -> i.getCredit() != null ? i.getCredit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebit.compareTo(totalCredit) != 0) {
            BigDecimal difference = totalDebit.subtract(totalCredit);
            log.warn("Unbalanced entry attempt: Debit={}, Credit={}, Diff={}",
                    totalDebit, totalCredit, difference);

            throw new InvalidOperationException(String.format(
                    "Journal entry is unbalanced. Debits: %s, Credits: %s, Difference: %s. " +
                            "Please ensure debits equal credits before submitting.",
                    totalDebit.setScale(2, RoundingMode.HALF_UP),
                    totalCredit.setScale(2, RoundingMode.HALF_UP),
                    difference.setScale(2, RoundingMode.HALF_UP)));
        }

        log.debug("Balance validation passed: Debits={}, Credits={}", totalDebit, totalCredit);
    }

    /**
     * Validates and retrieves the document type.
     * Ensures the document type belongs to the current company.
     */
    private DocumentType validateAndGetDocumentType(Long docTypeId, Company company) {
        DocumentType docType = docTypeService.findById(docTypeId);

        if (!docType.getCompany().getId().equals(company.getId())) {
            throw new InvalidOperationException(
                    "This Document Type does not belong to the current company.");
        }

        return docType;
    }

    /**
     * Creates the journal entry header with document number assignment.
     * Uses pessimistic locking to prevent duplicate consecutive numbers.
     */
    private JournalEntry createEntryHeader(DocumentType docType,
                                           JournalEntryRequest request,
                                           Company company) {
        JournalEntry entry = new JournalEntry();
        entry.setDocumentType(docType);
        entry.setEntryDate(request.getEntryDate());
        entry.setDescription(request.getDescription());
        entry.setCompany(company);

        // Get next consecutive number with pessimistic lock
        Long nextNumber = docTypeService.getNextConsecutive(docType.getId());
        entry.setConsecutive(nextNumber);
        entry.setDocumentNumber(generateDocNumber(docType, nextNumber));

        // Safety check for duplicate document numbers
        if (repository.existsByCompanyAndDocumentNumber(company, entry.getDocumentNumber())) {
            log.error("Duplicate document number detected: {}", entry.getDocumentNumber());
            throw new InvalidOperationException(
                    "Document number already exists. This may be a concurrency issue. Please retry.");
        }

        log.debug("Entry header created: DocType={}, DocNum={}, Date={}",
                docType.getCode(), entry.getDocumentNumber(), entry.getEntryDate());

        return entry;
    }

    /**
     * Processes and validates all line items.
     * Each item is validated for:
     * - Account existence and status
     * - Amount validity (one of debit/credit must be > 0)
     * - Third party requirement
     * - Cost center requirement
     */
    private void processLineItems(JournalEntry entry,
                                  JournalEntryRequest request,
                                  Company company) {
        for (int i = 0; i < request.getItems().size(); i++) {
            var itemDto = request.getItems().get(i);
            final int itemIndex = i;

            // Validate amounts (must have either debit OR credit, not both or neither)
            validateItemAmounts(itemDto.getDebit(), itemDto.getCredit(), itemIndex);

            // Fetch and validate account
            ChartOfAccounts account = fetchAndValidateAccount(itemDto.getAccountId(), company);

            // Create item
            JournalEntryItem item = new JournalEntryItem();
            item.setAccount(account);
            item.setDebit(scaleAmount(itemDto.getDebit()));
            item.setCredit(scaleAmount(itemDto.getCredit()));
            item.setDescription(itemDto.getDescription());

            // Handle optional fields based on account configuration
            handleThirdParty(item, itemDto, account, company);
            handleCostCenter(item, itemDto, account, company);

            entry.addItem(item);
        }

        log.debug("Processed {} line items", request.getItems().size());
    }

    /**
     * Scales monetary amount to 2 decimal places.
     * Ensures consistent precision across all financial calculations.
     */
    private BigDecimal scaleAmount(BigDecimal amount) {
        return amount != null
                ? amount.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    /**
     * Generates document number from document type prefix and consecutive.
     * Format: PREFIX-CONSECUTIVE (e.g., "CE-001", "EG-0045")
     */
    private String generateDocNumber(DocumentType docType, Long consecutive) {
        return (docType.getPrefix() != null && !docType.getPrefix().isBlank())
                ? docType.getPrefix().trim() + "-" + consecutive
                : consecutive.toString();
    }

    /**
     * Annuls a journal entry by neutralizing its financial impact.
     * * Business Rules:
     * 1. Entry's date must belong to an OPEN accounting period.
     * 2. All line item amounts are set to zero to neutralize the balance.
     * 3. Document metadata is preserved for audit and consecutive integrity.
     */
    @Transactional
    public JournalEntryResponseDTO annul(Long id, JournalEntryRequest.AnnulmentRequest annulRequest) {
        Company currentCompany = companyContext.getCurrentCompany();

        // 1. Fetch and validate ownership
        JournalEntry entry = repository.findById(id)
                .filter(e -> e.getCompany().getId().equals(currentCompany.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Journal Entry", id));

        // 2. PERIOD VALIDATION: Block annulment if the period is already closed
        accountingPeriodService.validateDateIsOpen(entry.getEntryDate(), currentCompany);

        // 3. STATE VALIDATION: Avoid double annulment or editing deleted records
        if (entry.isAnnulled()) {
            throw new InvalidOperationException("Journal entry is already annulled.");
        }

        if (!entry.isActive()) {
            throw new InvalidOperationException("Cannot annul an inactive (deleted) journal entry.");
        }

        log.info("Annuling journal entry {} (ID: {})", entry.getDocumentNumber(), id);

        // 4. FINANCIAL NEUTRALIZATION: Zero out debits and credits
        entry.setAnnulled(true);
        entry.setAnnulledAt(java.time.LocalDateTime.now());
        entry.setAnnulmentReason(annulRequest.getReason());

        for (JournalEntryItem item : entry.getItems()) {
            item.setDebit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            item.setCredit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }

        // 5. AUDIT LOGGING: Mark the description as annulled
        String prefix = "[ANNULLED] ";
        if (!entry.getDescription().startsWith(prefix)) {
            entry.setDescription(prefix + entry.getDescription());
        }

        JournalEntry savedEntry = repository.save(entry);
        log.info("Journal entry {} successfully neutralized.", savedEntry.getDocumentNumber());

        return mapToResponseDTO(savedEntry);
    }
    /**
     * Updates an existing journal entry.
     * * Business Rules:
     * 1. The current entry date must be in an OPEN period (cannot modify history).
     * 2. The new entry date must be in an OPEN period (cannot move data to locked periods).
     * 3. Total balance must remain zero (Debits = Credits).
     * 4. All business rules from 'create' are reapplied to the updated data.
     */
    @Transactional
    public JournalEntryResponseDTO update(Long id, JournalEntryRequest request) {
        Company currentCompany = companyContext.getCurrentCompany();

        // 1. Fetch existing entry and verify ownership
        JournalEntry existingEntry = repository.findById(id)
                .filter(e -> e.getCompany().getId().equals(currentCompany.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Journal Entry", id));

        // 2. PERIOD VALIDATION: Double-check strategy
        // First, check if the record we want to edit is already "locked" by a prior close
        accountingPeriodService.validateDateIsOpen(existingEntry.getEntryDate(), currentCompany);

        // Second, if changing the date, ensure the target date is also open
        if (!existingEntry.getEntryDate().equals(request.getEntryDate())) {
            validateEntryDate(request.getEntryDate()); // Reuses your 90-day/future validation
            accountingPeriodService.validateDateIsOpen(request.getEntryDate(), currentCompany);
        }

        // 3. FINANCIAL VALIDATION: Ensure the updated request is still balanced
        validateBalance(request);

        log.info("Updating journal entry {} (ID: {}) for company {}",
                existingEntry.getDocumentNumber(), id, currentCompany.getLegalName());

        // 4. HEADER UPDATE
        existingEntry.setEntryDate(request.getEntryDate());
        existingEntry.setDescription(request.getDescription());

        // 5. ITEMS UPDATE: Clear current items and re-process them to ensure full validation
        // This ensures third-party and cost-center rules are re-evaluated for the new accounts
        existingEntry.getItems().clear();
        processLineItems(existingEntry, request, currentCompany);

        JournalEntry saved = repository.save(existingEntry);
        log.info("Journal entry {} updated successfully", saved.getDocumentNumber());

        return mapToResponseDTO(saved);
    }

    // ═══════════════════════════════════════════════════════════
    // VALIDATION HELPERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Validates entry date constraints.
     *
     * Rules:
     * - Cannot be null
     * - Cannot be in the future
     * - Cannot be older than 90 days (configurable business rule)
     */
    private void validateEntryDate(LocalDate date) {
        if (date == null) {
            throw new InvalidOperationException("Entry date is required");
        }

        LocalDate today = LocalDate.now();

        if (date.isAfter(today)) {
            throw new InvalidOperationException("Entry date cannot be in the future");
        }

        // Business Rule: Prevent very old entries (data quality)
        LocalDate minAllowedDate = today.minusDays(90);
        if (date.isBefore(minAllowedDate)) {
            throw new InvalidOperationException(
                    String.format("Entry date cannot be older than %s (90 days back). " +
                            "For older dates, please contact system administrator.", minAllowedDate)
            );
        }

        log.debug("Entry date validated: {}", date);
    }

    /**
     * Validates that each item has either debit OR credit, but not both or neither.
     * This is a fundamental accounting principle.
     */
    private void validateItemAmounts(BigDecimal debit, BigDecimal credit, int itemIndex) {
        boolean hasDebit = debit != null && debit.compareTo(BigDecimal.ZERO) > 0;
        boolean hasCredit = credit != null && credit.compareTo(BigDecimal.ZERO) > 0;

        if (hasDebit && hasCredit) {
            throw new InvalidOperationException(
                    String.format("Item #%d cannot have both debit (%s) and credit (%s). " +
                                    "Each line must be either a debit OR a credit, not both.",
                            itemIndex + 1,
                            debit.setScale(2, RoundingMode.HALF_UP),
                            credit.setScale(2, RoundingMode.HALF_UP))
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
     * Fetches and validates an account for use in journal entries.
     *
     * Validations:
     * - Account exists
     * - Belongs to current company
     * - Is active (not deactivated)
     * - Is a posting account (Level 4+ auxiliary account)
     */
    private ChartOfAccounts fetchAndValidateAccount(Long accountId, Company company) {
        ChartOfAccounts account = accountRepository.findById(accountId)
                .filter(a -> a.getCompany().getId().equals(company.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

        if (!account.isActive()) {
            throw new InvalidOperationException(
                    String.format("Account %s - %s is inactive and cannot be used in journal entries.",
                            account.getCode(), account.getName())
            );
        }

        if (!account.isPostingAccount()) {
            throw new InvalidOperationException(
                    String.format("Account %s - %s is not a posting account. " +
                                    "Only auxiliary accounts (Level 4+) can be used in entries.",
                            account.getCode(), account.getName())
            );
        }

        return account;
    }

    /**
     * Handles third party validation and assignment.
     * If the account requires a third party, validates that one is provided.
     */
    private void handleThirdParty(JournalEntryItem item,
                                  JournalEntryRequest.ItemRequest dto,
                                  ChartOfAccounts account,
                                  Company company) {
        if (account.isRequiresThirdParty()) {
            if (dto.getThirdPartyId() == null) {
                throw new InvalidOperationException(
                        String.format("Account %s (%s) requires a Third Party to be specified.",
                                account.getCode(), account.getName())
                );
            }

            ThirdParty thirdParty = thirdPartyRepository.findById(dto.getThirdPartyId())
                    .filter(t -> t.getCompany().getId().equals(company.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("ThirdParty", dto.getThirdPartyId()));

            if (!thirdParty.getActive()) {
                throw new InvalidOperationException(
                        String.format("Third Party %s is inactive and cannot be used.",
                                thirdParty.getLegalDisplayName())
                );
            }

            item.setThirdParty(thirdParty);
        }
    }

    /**
     * Handles cost center validation and assignment.
     * If the account requires a cost center, validates that one is provided.
     */
    private void handleCostCenter(JournalEntryItem item,
                                  JournalEntryRequest.ItemRequest dto,
                                  ChartOfAccounts account,
                                  Company company) {
        if (account.isRequiresCostCenter()) {
            if (dto.getCostCenterId() == null) {
                throw new InvalidOperationException(
                        String.format("Account %s (%s) requires a Cost Center to be specified.",
                                account.getCode(), account.getName())
                );
            }

            CostCenter costCenter = costCenterRepository.findById(dto.getCostCenterId())
                    .filter(c -> c.getCompany().getId().equals(company.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("CostCenter", dto.getCostCenterId()));

            if (!costCenter.isActive()) {
                throw new InvalidOperationException(
                        String.format("Cost Center %s is inactive and cannot be used.",
                                costCenter.getName())
                );
            }

            item.setCostCenter(costCenter);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // READ OPERATIONS
    // ═══════════════════════════════════════════════════════════

    /**
     * Lists journal entries with optional filtering and pagination.
     *
     * @param searchTerm Optional search term (searches document number and description)
     * @param start Optional start date filter
     * @param end Optional end date filter
     * @param pageable Pagination parameters
     * @return Paginated list of journal entries
     */
    public Page<JournalEntryResponseDTO> listEntries(String searchTerm,
                                                     LocalDate start,
                                                     LocalDate end,
                                                     Pageable pageable) {
        Company company = companyContext.getCurrentCompany();

        log.debug("Listing journal entries for company: {} with filters - search: {}, dates: {} to {}",
                company.getId(), searchTerm, start, end);

        return repository.searchEntries(company, searchTerm, start, end, pageable)
                .map(this::mapToResponseDTO);
    }

    /**
     * Finds a journal entry by document number.
     *
     * @param docNum Document number (e.g., "CE-001")
     * @return Journal entry details
     * @throws ResourceNotFoundException if not found
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
     * Finds a specific journal entry by its internal ID.
     * Validates that the entry belongs to the current user's company.
     *
     * @param id The database primary key
     * @return Mapped Response DTO
     * @throws ResourceNotFoundException if not found or belongs to another company
     */
    public JournalEntryResponseDTO findById(Long id) {
        Company company = companyContext.getCurrentCompany();

        return repository.findById(id)
                .filter(entry -> entry.getCompany().getId().equals(company.getId()))
                .map(this::mapToResponseDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Journal Entry", id));
    }

    // ═══════════════════════════════════════════════════════════
    // TRIAL BALANCE REPORTS
    // ═══════════════════════════════════════════════════════════

    /**
     * Generates Trial Balance Report as of a specific date.
     *
     * Shows cumulative balances for all accounts up to the specified date:
     * - Account code and name
     * - Total debits
     * - Total credits
     * - Net balance
     * - Summary by account class
     *
     * @param asOfDate Date for the trial balance (null = current date)
     * @return Complete trial balance report
     */
    public TrialBalanceReport getTrialBalanceReport(LocalDate asOfDate) {
        Company company = companyContext.getCurrentCompany();

        // Default to current date if not provided
        if (asOfDate == null) {
            asOfDate = LocalDate.now();
        }

        log.info("Generating Trial Balance for company: {} as of {}", company.getId(), asOfDate);

        // Get trial balance lines from repository
        List<TrialBalanceLine> lines = accountRepository.getTrialBalance(company, asOfDate);

        // Calculate totals
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (TrialBalanceLine line : lines) {
            totalDebit = totalDebit.add(line.getTotalDebit());
            totalCredit = totalCredit.add(line.getTotalCredit());
        }

        // Generate summary by account class
        Map<String, BigDecimal> summary = new HashMap<>();
        int invalidCount = 0;

        for (TrialBalanceLine line : lines) {
            if (line.getNetBalance() != null) {
                String className = getAccountClassName(line.getAccountCode());
                summary.merge(className, line.getNetBalance(), BigDecimal::add);
            } else {
                invalidCount++;
            }
        }

        // Log if any invalid data was found
        if (invalidCount > 0) {
            log.warn("Found {} trial balance lines with null net balance", invalidCount);
        }

        // Check if balanced
        boolean isBalanced = totalDebit.setScale(2, RoundingMode.HALF_UP)
                .compareTo(totalCredit.setScale(2, RoundingMode.HALF_UP)) == 0;

        if (!isBalanced) {
            log.warn("TRIAL BALANCE OUT OF BALANCE! Debit: {}, Credit: {}, Difference: {}",
                    totalDebit, totalCredit, totalDebit.subtract(totalCredit));
        }

        // Build the report
        return TrialBalanceReport.builder()
                .companyName(company.getLegalName())
                .asOfDate(asOfDate)
                .generatedAt(LocalDate.now())
                .lines(lines)
                .totalDebit(totalDebit)
                .totalCredit(totalCredit)
                .isBalanced(isBalanced)
                .summary(summary)
                .build();
    }

    /**
     * Generates detailed Trial Balance with opening balances for a date range.
     *
     * Shows:
     * - Opening balance (as of day before startDate)
     * - Period activity (from startDate to endDate)
     * - Closing balance (as of endDate)
     *
     * Business Rules:
     * - Balance Sheet accounts (1,2,3): Opening balance = actual prior balance
     * - Income Statement accounts (4,5,6,7): Opening balance = ZERO (temporary accounts)
     *
     * @param startDate Start of the reporting period
     * @param endDate End of the reporting period
     * @return Detailed trial balance report
     */
    public TrialBalanceReportDetailed getTrialBalanceDetailed(LocalDate startDate,
                                                              LocalDate endDate) {
        Company company = companyContext.getCurrentCompany();

        // Validation
        if (startDate == null || endDate == null) {
            throw new InvalidOperationException("Start date and end date are required");
        }

        if (startDate.isAfter(endDate)) {
            throw new InvalidOperationException("Start date cannot be after end date");
        }

        log.info("Generating detailed Trial Balance for company: {} from {} to {}",
                company.getId(), startDate, endDate);

        // 1. Get opening balances (only for Balance Sheet accounts - classes 1,2,3)
        Map<String, BigDecimal> openingBalances = new HashMap<>();
        List<Object[]> openings = accountRepository.getOpeningBalances(company, startDate);

        for (Object[] row : openings) {
            String code = (String) row[0];
            BigDecimal balance = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
            openingBalances.put(code, balance.setScale(2, RoundingMode.HALF_UP));
        }

        log.debug("Retrieved opening balances for {} Balance Sheet accounts", openingBalances.size());

        // 2. Get period activity (all accounts with activity in the period)
        List<Object[]> activities = accountRepository.getPeriodActivity(company, startDate, endDate);

        List<TrialBalanceLineDetailed> lines = new ArrayList<>();
        BigDecimal totalOpeningBalance = BigDecimal.ZERO;
        BigDecimal totalPeriodDebit = BigDecimal.ZERO;
        BigDecimal totalPeriodCredit = BigDecimal.ZERO;
        BigDecimal totalNetMovement = BigDecimal.ZERO;
        BigDecimal totalClosingBalance = BigDecimal.ZERO;

        Map<String, BigDecimal> summaryByClass = new HashMap<>();

        for (Object[] row : activities) {
            String code = (String) row[0];
            String name = (String) row[1];
            AccountClass accountClass = (AccountClass) row[2];
            boolean closesAtYearEnd = (boolean) row[3];
            BigDecimal periodDebit = row[4] != null ? (BigDecimal) row[4] : BigDecimal.ZERO;
            BigDecimal periodCredit = row[5] != null ? (BigDecimal) row[5] : BigDecimal.ZERO;

            periodDebit = periodDebit.setScale(2, RoundingMode.HALF_UP);
            periodCredit = periodCredit.setScale(2, RoundingMode.HALF_UP);

            // CRITICAL BUSINESS RULE:
            // Opening balance = 0 for Income Statement accounts (4,5,6,7) - temporary accounts
            // Opening balance = actual for Balance Sheet accounts (1,2,3) - permanent accounts
            BigDecimal opening = closesAtYearEnd
                    ? BigDecimal.ZERO
                    : openingBalances.getOrDefault(code, BigDecimal.ZERO);

            BigDecimal netMovement = periodDebit.subtract(periodCredit);
            BigDecimal closing = opening.add(netMovement);

            String classDisplay = getAccountClassDisplay(accountClass);

            TrialBalanceLineDetailed line = TrialBalanceLineDetailed.builder()
                    .accountCode(code)
                    .accountName(name)
                    .accountClass(classDisplay)
                    .isBalanceSheetAccount(!closesAtYearEnd)
                    .openingBalance(opening)
                    .periodDebit(periodDebit)
                    .periodCredit(periodCredit)
                    .netMovement(netMovement)
                    .closingBalance(closing)
                    .build();

            lines.add(line);

            // Update totals
            totalOpeningBalance = totalOpeningBalance.add(opening);
            totalPeriodDebit = totalPeriodDebit.add(periodDebit);
            totalPeriodCredit = totalPeriodCredit.add(periodCredit);
            totalNetMovement = totalNetMovement.add(netMovement);
            totalClosingBalance = totalClosingBalance.add(closing);

            // Update summary by class
            summaryByClass.merge(classDisplay, closing, BigDecimal::add);
        }

        // Check if period is balanced (debits should equal credits)
        boolean isBalanced = totalPeriodDebit.setScale(2, RoundingMode.HALF_UP)
                .compareTo(totalPeriodCredit.setScale(2, RoundingMode.HALF_UP)) == 0;

        if (!isBalanced) {
            log.warn("DETAILED TRIAL BALANCE OUT OF BALANCE! Period Debit: {}, Period Credit: {}, Difference: {}",
                    totalPeriodDebit, totalPeriodCredit, totalPeriodDebit.subtract(totalPeriodCredit));
        }

        log.info("Detailed Trial Balance generated: {} accounts, Balanced: {}", lines.size(), isBalanced);

        return TrialBalanceReportDetailed.builder()
                .companyName(company.getLegalName())
                .startDate(startDate)
                .endDate(endDate)
                .generatedAt(LocalDate.now())
                .lines(lines)
                .totalOpeningBalance(totalOpeningBalance.setScale(2, RoundingMode.HALF_UP))
                .totalPeriodDebit(totalPeriodDebit.setScale(2, RoundingMode.HALF_UP))
                .totalPeriodCredit(totalPeriodCredit.setScale(2, RoundingMode.HALF_UP))
                .totalNetMovement(totalNetMovement.setScale(2, RoundingMode.HALF_UP))
                .totalClosingBalance(totalClosingBalance.setScale(2, RoundingMode.HALF_UP))
                .isBalanced(isBalanced)
                .summaryByClass(summaryByClass)
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // HELPER METHODS FOR REPORTS
    // ═══════════════════════════════════════════════════════════

    /**
     * Helper method to get display name for account class.
     */
    private String getAccountClassDisplay(AccountClass accountClass) {
        return switch (accountClass) {
            case ASSET -> "1 - Assets";
            case LIABILITY -> "2 - Liabilities";
            case EQUITY -> "3 - Equity";
            case REVENUE -> "4 - Revenue";
            case EXPENSE -> "5 - Expenses";
            case COST -> "6/7 - Costs";
        };
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
            case '4' -> "4 - Revenue";
            case '5' -> "5 - Expenses";
            case '6', '7' -> "6/7 - Costs";
            default -> "Other";
        };
    }

    // ═══════════════════════════════════════════════════════════
    // DTO MAPPING
    // ═══════════════════════════════════════════════════════════

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