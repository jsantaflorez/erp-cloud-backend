package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.*;
import com.erp.erp_cloud.dto.reports.financial.TrialBalanceLineDetailed;
import com.erp.erp_cloud.dto.reports.financial.TrialBalanceReportDetailed;
import com.erp.erp_cloud.entity.*;
import com.erp.erp_cloud.enums.AccountClass;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.exception.ResourceNotFoundException;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.repository.CompanyRepository;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.repository.CostCenterRepository;
import com.erp.erp_cloud.repository.ThirdPartyRepository;
import com.erp.erp_cloud.repository.TaxRepository;
import com.erp.erp_cloud.dto.TrialBalanceLine;
import com.erp.erp_cloud.dto.reports.financial.TrialBalanceReport;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.erp.erp_cloud.service.base.TenantAwareService;
import com.erp.erp_cloud.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
 * Current Version:
 * - No automatic balancing -- full accountant control over every line.
 * - The one exception (2026-10-14): a line whose account is
 *   parameterized with a Tax (see handleTax()) has its debit/credit
 *   computed automatically from a user-entered base gravable, so the
 *   fiscal reports (Informe de IVA, Informe/Certificado de
 *   Retenciones) have a reliable base + amount to read, instead of
 *   reverse-engineering the base from whatever got posted.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JournalEntryService extends TenantAwareService {

    private static final Logger log = LoggerFactory.getLogger(JournalEntryService.class);

    private final JournalEntryRepository repository;
    private final ChartOfAccountsRepository accountRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final CostCenterRepository costCenterRepository;
    private final DocumentTypeService docTypeService;
    private final AccountingPeriodService accountingPeriodService;
    private final CompanyRepository companyRepository;
    private final TaxRepository taxRepository;
    // Bloque A (2026-11-21): saves the previous version before an edit/annul.
    private final JournalEntryHistoryService historyService;

    // ═══════════════════════════════════════════════════════════
    // CREATE OPERATIONS
    // ═══════════════════════════════════════════════════════════

    /**
     * Creates a new journal entry with strict manual validation.
     */
    @Transactional
    public JournalEntryResponseDTO create(JournalEntryRequest request) {
        return create(request, null);
    }

    /**
     * Bloque A (2026-11-21, F1-03): with an idempotency key, a request that
     * already created an entry returns that same entry instead of creating
     * a second one (double Ctrl+Enter, network retry). Two copies racing at
     * the same instant both pass this lookup; the second then fails the
     * UNIQUE (company_id, idempotency_key) and the controller answers it
     * with the entry the first one created (findByIdempotencyKey).
     */
    @Transactional
    public JournalEntryResponseDTO create(JournalEntryRequest request, String idempotencyKey) {
        Long companyId = currentTenantId();
        String key = normalizeIdempotencyKey(idempotencyKey);
        if (key != null) {
            Optional<JournalEntry> existing = repository.findByCompanyIdAndIdempotencyKey(companyId, key);
            if (existing.isPresent()) {
                log.info("Idempotent replay: key already used by entry {} (company {})",
                        existing.get().getDocumentNumber(), companyId);
                return mapToResponseDTO(existing.get());
            }
        }
        log.info("Creating journal entry for company ID: {}", companyId);

        // 1. Date and Period Validation
        validateEntryDate(request.getEntryDate());
        accountingPeriodService.validateDateIsOpen(request.getEntryDate(), companyId);


        // 2. Items Existence Validation
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new InvalidOperationException("Journal entry must contain at least one item.");
        }

        // 3. Document Type Setup and Validation
        DocumentType docType = validateAndGetDocumentType(request.getDocumentTypeId(), companyId);

        // 4. Create Entry Header
        JournalEntry entry = createEntryHeader(docType, request, companyId);
        entry.setIdempotencyKey(key);

        // 5. Process Line Items
        processLineItems(entry, request, companyId);

        // 6. CRITICAL: Balance Validation (Zero Tolerance). Deliberately
        // run AFTER processLineItems, not on the raw request -- a
        // tax-linked line's real debit/credit are only known once
        // handleTax() has recalculated them from the base (see
        // processLineItems()); checking the client's raw submitted
        // amounts here would reject a perfectly balanced tax-linked
        // entry whose raw numbers don't happen to match what the server
        // is about to compute.
        validateBalance(entry.getItems());

        // 7. Save and Return
        log.info("Saving journal entry {} with {} items for company ID {}",
                entry.getDocumentNumber(), entry.getItems().size(), companyId);

        return mapToResponseDTO(repository.save(entry));
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE CREATION HELPERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Validates that total debits equal total credits.
     *
     * NEW (2026-10-14): takes the entry's own items, not the raw
     * request -- must run after processLineItems() so a tax-linked
     * line's amount reflects handleTax()'s server-side recalculation
     * from the base, not whatever (possibly irrelevant) debit/credit
     * the client happened to submit for that line.
     */
    private void validateBalance(List<JournalEntryItem> items) {
        BigDecimal totalDebit = items.stream()
                .map(i -> i.getDebit() != null ? i.getDebit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredit = items.stream()
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
     */
    private DocumentType validateAndGetDocumentType(Long docTypeId, Long companyId) {
        DocumentType docType = docTypeService.findById(docTypeId);

        if (!docType.getCompany().getId().equals(companyId)) {
            throw new InvalidOperationException("This Document Type does not belong to the current company.");
        }

        // NEW (2026-10-13, user request): CIERRE is reserved for the
        // system's own year-end closing process (see
        // AccountingPeriodService.buildAndSaveClosingEntry(), which saves
        // directly through journalEntryRepository and never goes through
        // this create() path -- so this guard can never block the real
        // automatic closing). A user manually picking CIERRE here would
        // produce a hand-made entry indistinguishable from the system's
        // own closing entry, yet never actually tied to a year-end
        // calculation -- confusing at best, and it would itself become
        // permanently immutable per CLOSING_ENTRY_IMMUTABLE above with no
        // way to fix a mistake in it.
        if (DocumentTypeService.CLOSING_DOC_TYPE_CODE.equals(docType.getCode())) {
            throw new InvalidOperationException(
                    "The year-end closing document type is reserved for the automatic closing process and cannot be used for manual entries.",
                    "CLOSING_DOC_TYPE_RESERVED");
        }

        // NEW (2026-11-12): same reasoning as CLOSING_DOC_TYPE_RESERVED
        // above, for the opening-balance document type used by the
        // SIEWIN balances migration importer (SiewinBalanceImportService,
        // which -- like AccountingPeriodService.buildAndSaveClosingEntry()
        // for CIERRE -- saves directly through the repository and never
        // goes through this create() path, so this guard can never block
        // the real automatic import/replace).
        if (DocumentTypeService.OPENING_DOC_TYPE_CODE.equals(docType.getCode())) {
            throw new InvalidOperationException(
                    "The opening-balance document type is reserved for the SIEWIN balances migration process and cannot be used for manual entries.",
                    "OPENING_DOC_TYPE_RESERVED");
        }

        // Bloque A fase 3 (2026-11-24): ANULACION is reserved for the reverse
        // entries annul() creates; a hand-made one would cancel nothing.
        if (DocumentTypeService.REVERSAL_DOC_TYPE_CODE.equals(docType.getCode())) {
            throw new InvalidOperationException(
                    "The annulment document type is reserved for the reverse entries created when a document is annulled.",
                    "REVERSAL_DOC_TYPE_RESERVED");
        }

        return docType;
    }

    /**
     * Creates the journal entry header with document number assignment.
     */
    private JournalEntry createEntryHeader(DocumentType docType, JournalEntryRequest request, Long companyId) {
        JournalEntry entry = new JournalEntry();
        entry.setDocumentType(docType);
        entry.setEntryDate(request.getEntryDate());
        entry.setDescription(request.getDescription());

        // BUG FIX: company was never actually assigned here (only mentioned in a
        // comment), so every journal entry save hit the DB's NOT NULL constraint on
        // company_id and surfaced as a confusing "required field missing" error with
        // no field the user could see on the form. Same class of bug already fixed
        // this session in TaxService/ThirdPartyService.
        entry.setCompany(companyRepository.getReferenceById(companyId));

        // Get next consecutive number with pessimistic lock
        Long nextNumber = docTypeService.getNextConsecutive(docType.getId());
        entry.setConsecutive(nextNumber);
        entry.setDocumentNumber(generateDocNumber(docType, nextNumber));

        // Safety check for duplicate document numbers
        if (repository.existsByCompanyIdAndDocumentNumber(companyId, entry.getDocumentNumber())) {
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
     */
    private void processLineItems(JournalEntry entry, JournalEntryRequest request, Long companyId) {
        processLineItems(entry, request, companyId, Set.of());
    }

    /**
     * @param keptInactiveThirdPartyIds third parties that were ALREADY on the
     *        entry being edited (2026-11-04). They may stay on it even if they
     *        were deactivated afterwards; any other inactive third party is
     *        still rejected. Empty for new entries (create/duplicate).
     */
    private void processLineItems(JournalEntry entry, JournalEntryRequest request, Long companyId,
                                  Set<Long> keptInactiveThirdPartyIds) {
        for (int i = 0; i < request.getItems().size(); i++) {
            var itemDto = request.getItems().get(i);
            final int itemIndex = i;

            // Fetch and validate account
            ChartOfAccounts account = fetchAndValidateAccount(itemDto.getAccountId(), companyId);

            // Create item
            JournalEntryItem item = new JournalEntryItem();
            item.setAccount(account);
            item.setDebit(scaleAmount(itemDto.getDebit()));
            item.setCredit(scaleAmount(itemDto.getCredit()));
            item.setDescription(itemDto.getDescription());

            // Handle optional fields based on account configuration
            handleThirdParty(item, itemDto, account, companyId, keptInactiveThirdPartyIds);
            handleCostCenter(item, itemDto, account, companyId);

            // Tax-linked accounts: derive the Tax from the account and
            // recalculate debit/credit from the submitted base. Must run
            // before validateItemAmounts() below, since it may overwrite
            // item.debit/item.credit with the server-computed amounts.
            handleTax(item, itemDto, account, companyId);

            // Validate amounts (on the item's final values: either the
            // client-submitted debit/credit, or -- for a tax-linked line --
            // the amounts handleTax() just computed from the base).
            validateItemAmounts(item.getDebit(), item.getCredit(), itemIndex);

            entry.addItem(item);
        }

        log.debug("Processed {} line items", request.getItems().size());
    }

    private BigDecimal scaleAmount(BigDecimal amount) {
        return amount != null ? amount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    private String generateDocNumber(DocumentType docType, Long consecutive) {
        return (docType.getPrefix() != null && !docType.getPrefix().isBlank())
                ? docType.getPrefix().trim() + "-" + consecutive
                : consecutive.toString();
    }

    // ═══════════════════════════════════════════════════════════
    // UPDATE & ANNUL OPERATIONS
    // ═══════════════════════════════════════════════════════════

    /**
     * Annuls a journal entry by REVERSE (Bloque A fase 3, 2026-11-24).
     *
     * The original stays exactly as it was posted -- only marked annulled,
     * with who, when and why -- and a new entry of the reserved ANULACION
     * type (ANU-n, its own consecutive) is posted on the SAME date with every
     * line's debit and credit swapped. Together they net to zero in every
     * balance, while the books still show what was posted and what cancelled
     * it (decisions 2 and 3 of the user, 2026-11-21).
     *
     * Before V9 this zeroed the original's lines in place. The system
     * processes that annul on their own (CIERRE recalculation, reopenYear,
     * SIEWIN APERTURA replacement) still do that -- user decision 2026-11-24.
     */
    @Transactional
    public JournalEntryResponseDTO annul(Long id, JournalEntryRequest.AnnulmentRequest annulRequest) {
        Long companyId = currentTenantId();

        // 1. Fetch and validate ownership
        JournalEntry entry = repository.findById(id)
                .filter(e -> e.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Journal Entry", id));

        // 2. PERIOD VALIDATION
        accountingPeriodService.validateDateIsOpen(entry.getEntryDate(), companyId);

        // 3. STATE VALIDATION
        if (entry.isAnnulled()) {
            throw new InvalidOperationException(
                    "Journal entry is already annulled.", "ENTRY_ALREADY_ANNULLED");
        }

        if (!entry.isActive()) {
            throw new InvalidOperationException(
                    "Cannot annul an inactive (deleted) journal entry.", "ENTRY_INACTIVE_IMMUTABLE");
        }

        // A reverse entry cancels another one; annulling it would bring the
        // original's effect back without un-annulling the original.
        if (entry.isReversal()) {
            throw new InvalidOperationException(
                    "A reverse (annulment) entry cannot be annulled.", "REVERSAL_ENTRY_IMMUTABLE");
        }

        log.info("Annulling journal entry {} (ID: {}) by reverse", entry.getDocumentNumber(), id);

        // Bloque A (2026-11-21): keep the entry as it was, before annulling.
        historyService.record(entry, mapToResponseDTO(entry), JournalEntryHistory.ACTION_ANNUL);

        // 4. REVERSE ENTRY (same date, debits and credits swapped)
        JournalEntry reversal = repository.save(buildReversalEntry(entry, annulRequest.getReason(), companyId));

        // 5. MARK THE ORIGINAL -- its lines are NOT touched any more.
        entry.setAnnulled(true);
        entry.setAnnulledAt(java.time.LocalDateTime.now());
        // FIX (2026-11-19, auditoria F1-02 / Bloque 0.2): a manual annulment
        // never recorded WHO did it -- annulled_by stayed NULL; only the
        // system processes (year close, SIEWIN re-import) filled it in.
        entry.setAnnulledBy(currentActor());
        entry.setAnnulmentReason(annulRequest.getReason());
        entry.setReversalEntry(reversal);

        JournalEntry savedEntry = repository.save(entry);
        log.info("Journal entry {} annulled by reverse {}.",
                savedEntry.getDocumentNumber(), reversal.getDocumentNumber());

        return mapToResponseDTO(savedEntry);
    }

    /**
     * Builds the ANULACION entry that cancels {@code original}: same date,
     * same accounts / third parties / cost centers / tax link, debit and
     * credit swapped on every line. Never goes through create(): it must use
     * the reserved type create() refuses, and its lines are a mirror of an
     * entry that was already validated when it was posted.
     */
    private JournalEntry buildReversalEntry(JournalEntry original, String reason, Long companyId) {
        DocumentType docType = docTypeService.findOrCreateReversalDocumentType(companyId);

        JournalEntry reversal = new JournalEntry();
        reversal.setCompany(companyRepository.getReferenceById(companyId));
        reversal.setDocumentType(docType);
        reversal.setEntryDate(original.getEntryDate());
        reversal.setReversalOf(original);

        Long nextNumber = docTypeService.getNextConsecutive(docType.getId());
        reversal.setConsecutive(nextNumber);
        reversal.setDocumentNumber(generateDocNumber(docType, nextNumber));
        if (repository.existsByCompanyIdAndDocumentNumber(companyId, reversal.getDocumentNumber())) {
            log.error("Duplicate document number detected: {}", reversal.getDocumentNumber());
            throw new InvalidOperationException(
                    "Document number already exists. This may be a concurrency issue. Please retry.");
        }

        String description = "Anulación de " + original.getDocumentNumber()
                + (reason != null && !reason.isBlank() ? ": " + reason.trim() : "");
        reversal.setDescription(description.length() > 255 ? description.substring(0, 255) : description);

        for (JournalEntryItem source : original.getItems()) {
            JournalEntryItem line = new JournalEntryItem();
            line.setAccount(source.getAccount());
            line.setDebit(scaleAmount(source.getCredit()));
            line.setCredit(scaleAmount(source.getDebit()));
            line.setDescription(source.getDescription());
            line.setThirdParty(source.getThirdParty());
            line.setCostCenter(source.getCostCenter());
            line.setTax(source.getTax());
            line.setTaxBase(source.getTaxBase());
            reversal.addItem(line);
        }
        // Mirror of a balanced entry is balanced; checked anyway, it is cheap.
        validateBalance(reversal.getItems());
        return reversal;
    }

    /**
     * Duplicates an existing journal entry onto a brand-new one at a
     * caller-chosen date.
     *
     * NEW (2026-10-22, user request): useful for repetitive entries
     * (nomina, arriendo, depreciacion) instead of retyping the same
     * lines every period. Deliberately does NOT reimplement any
     * validation itself -- it builds a JournalEntryRequest from the
     * source entry's current document type/description/items and
     * delegates straight to create(), so the duplicate is subject to
     * every rule a hand-typed entry would be: the new date must be in
     * an open period (accountingPeriodService.validateDateIsOpen(),
     * via create()), the entry must balance, and CIERRE can't be used
     * (validateAndGetDocumentType() already rejects it for ANY
     * create(), so a closing entry's documentTypeId gets refused here
     * for free, with no separate check needed).
     */
    @Transactional
    public JournalEntryResponseDTO duplicate(Long id, JournalEntryRequest.DuplicateRequest duplicateRequest) {
        return duplicate(id, duplicateRequest, null);
    }

    @Transactional
    public JournalEntryResponseDTO duplicate(Long id, JournalEntryRequest.DuplicateRequest duplicateRequest,
                                             String idempotencyKey) {
        Long companyId = currentTenantId();

        JournalEntry source = repository.findById(id)
                .filter(e -> e.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Journal Entry", id));

        // An annulled entry is not a model to repeat (and entries annulled
        // before V9, or by a system process, have all-zero lines). Blocked
        // with a clear message.
        if (source.isAnnulled()) {
            throw new InvalidOperationException(
                    "An annulled journal entry cannot be duplicated.", "CANNOT_DUPLICATE_ANNULLED_ENTRY");
        }
        // Bloque A fase 3: a reverse entry uses the reserved ANULACION type.
        if (source.isReversal()) {
            throw new InvalidOperationException(
                    "A reverse (annulment) entry cannot be duplicated.", "REVERSAL_ENTRY_IMMUTABLE");
        }

        // NEW (2026-10-24, user request): the "Duplicar Comprobante" modal
        // now shows the source entry's description, editable, instead of
        // silently carrying it forward -- and whatever the user confirms
        // (accepted as-is, or changed) becomes BOTH the new entry's header
        // description AND every line's own description, replacing each
        // line's original note. A null override (any caller that doesn't
        // send one) falls back to the original behavior: header AND each
        // line keep exactly what the source entry had.
        String overrideDescription = duplicateRequest.getDescription();

        JournalEntryRequest request = new JournalEntryRequest();
        request.setEntryDate(duplicateRequest.getEntryDate());
        request.setDocumentTypeId(source.getDocumentType().getId());
        request.setDescription(overrideDescription != null ? overrideDescription : source.getDescription());
        request.setItems(source.getItems().stream().map(item -> {
            JournalEntryRequest.ItemRequest itemRequest = new JournalEntryRequest.ItemRequest();
            itemRequest.setAccountId(item.getAccount().getId());
            itemRequest.setThirdPartyId(item.getThirdParty() != null ? item.getThirdParty().getId() : null);
            itemRequest.setCostCenterId(item.getCostCenter() != null ? item.getCostCenter().getId() : null);
            itemRequest.setDebit(item.getDebit() != null ? item.getDebit() : BigDecimal.ZERO);
            itemRequest.setCredit(item.getCredit() != null ? item.getCredit() : BigDecimal.ZERO);
            itemRequest.setDescription(overrideDescription != null ? overrideDescription : item.getDescription());
            // handleTax() recomputes debit/credit from this base against
            // the account's CURRENT tax rate during create() below -- if
            // the rate changed since the source entry was posted, the
            // duplicate intentionally reflects today's rate, not the
            // stale one the source was created under.
            itemRequest.setTaxBase(item.getTaxBase());
            return itemRequest;
        }).toList());

        log.info("Duplicating journal entry {} (ID: {}) onto {}",
                source.getDocumentNumber(), id, duplicateRequest.getEntryDate());

        return create(request, idempotencyKey);
    }

    /**
     * The entry a given idempotency key created, if any (Bloque A, F1-03).
     * Used by the controller to answer the losing copy of two identical
     * requests that raced past the lookup in create().
     */
    @Transactional(readOnly = true)
    public Optional<JournalEntryResponseDTO> findByIdempotencyKey(String idempotencyKey) {
        String key = normalizeIdempotencyKey(idempotencyKey);
        if (key == null) {
            return Optional.empty();
        }
        return repository.findByCompanyIdAndIdempotencyKey(currentTenantId(), key)
                .map(this::mapToResponseDTO);
    }

    /** Null/blank = no key. Longer than the column (64) = rejected, never truncated. */
    private static String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String key = idempotencyKey.trim();
        if (key.length() > 64) {
            throw new InvalidOperationException("Idempotency key too long (max 64).", "INVALID_IDEMPOTENCY_KEY");
        }
        return key;
    }

    /**
     * Updates an existing journal entry.
     */
    @Transactional
    public JournalEntryResponseDTO update(Long id, JournalEntryRequest request) {
        Long companyId = currentTenantId();

        // 1. Fetch existing entry and verify ownership
        JournalEntry existingEntry = repository.findById(id)
                .filter(e -> e.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Journal Entry", id));

        // 2. STATE VALIDATION
        // BUG FIX: an annulled entry's financial effect is permanently
        // neutralized by annul() (since V9 by its ANULACION reverse; before,
        // and in the system processes, by zeroing its lines) --
        // editing it here would silently undo that neutralization and
        // corrupt the audit trail. Mirrors the guard annul() already
        // applies to itself.
        if (existingEntry.isAnnulled()) {
            throw new InvalidOperationException(
                    "Cannot edit an annulled journal entry.", "ENTRY_ANNULLED_IMMUTABLE");
        }
        // Bloque A fase 3: the reverse must stay the exact mirror of its original.
        if (existingEntry.isReversal()) {
            throw new InvalidOperationException(
                    "A reverse (annulment) entry cannot be edited.", "REVERSAL_ENTRY_IMMUTABLE");
        }
        if (!existingEntry.isActive()) {
            throw new InvalidOperationException(
                    "Cannot edit an inactive journal entry.", "ENTRY_INACTIVE_IMMUTABLE");
        }

        // NEW (2026-10-12, user request): a year-end closing entry
        // ("CIERRE", see DocumentTypeService.findOrCreateClosingDocumentType()
        // / AccountingPeriodService.closeYear()) is an auto-generated,
        // system-computed entry that zeroes every Ingresos/Gastos/Costos
        // account to move the net result into Patrimonio. Hand-editing it
        // would desync it from the closing calculation it represents and
        // corrupt the audit trail -- same reasoning as the
        // ENTRY_ANNULLED_IMMUTABLE/ENTRY_INACTIVE_IMMUTABLE guards above.
        // documentType is FetchType.LAZY, but update() runs inside its own
        // @Transactional, so the lazy load here is safe.
        if (DocumentTypeService.CLOSING_DOC_TYPE_CODE.equals(existingEntry.getDocumentType().getCode())) {
            throw new InvalidOperationException(
                    "Cannot edit a year-end closing entry.", "CLOSING_ENTRY_IMMUTABLE");
        }

        // DECISION (2026-11-19, user): the opening-balance entry
        // ("APERTURA") generated by the SIEWIN migration CAN be edited by
        // hand, for small adjustments without re-uploading the file. It is
        // deliberately NOT blocked like CIERRE above. The migration screen
        // warns that migrating again replaces the entry completely (the
        // current one is annulled), so manual adjustments are lost then.
        // Creating a NEW manual entry with the APERTURA type is still
        // rejected in validateAndGetDocumentType() (OPENING_DOC_TYPE_RESERVED).

        // Bloque A (2026-11-21, F1-20): the client sends the version it
        // loaded. If someone saved the entry after that, refuse instead of
        // silently replacing their lines with this older copy.
        if (request.getVersion() != null && !request.getVersion().equals(existingEntry.getVersion())) {
            throw new InvalidOperationException(
                    "This entry was modified by another user. Reload it and try again.",
                    "ENTRY_MODIFIED_BY_OTHER_USER");
        }

        // 3. PERIOD VALIDATION
        accountingPeriodService.validateDateIsOpen(existingEntry.getEntryDate(), companyId);

        if (!existingEntry.getEntryDate().equals(request.getEntryDate())) {
            validateEntryDate(request.getEntryDate());
            accountingPeriodService.validateDateIsOpen(request.getEntryDate(), companyId);
        }

        log.info("Updating journal entry {} (ID: {}) for company ID {}",
                existingEntry.getDocumentNumber(), id, companyId);

        // Bloque A (2026-11-21, decision del usuario): editable while the
        // month is open, but the previous version is saved complete first
        // (header + every line, who and when). Same transaction: no edit
        // can be saved without its snapshot.
        historyService.record(existingEntry, mapToResponseDTO(existingEntry), JournalEntryHistory.ACTION_UPDATE);

        // 4. HEADER UPDATE
        // FIX (2026-11-21, encontrado probando con Chrome + Firefox): when an
        // edit only changes LINES (amounts, accounts) and leaves the header
        // as it was, Hibernate did not increment @Version -- the lines are a
        // separate (mappedBy) collection -- so a second browser holding the
        // same old version saved over the first one without any warning.
        // Touching updated_at makes the header itself dirty, so EVERY edit
        // bumps the version, whatever field it changed.
        existingEntry.setUpdatedAt(java.time.LocalDateTime.now());
        existingEntry.setEntryDate(request.getEntryDate());
        existingEntry.setDescription(request.getDescription());

        // 5. ITEMS UPDATE
        // Third parties already on this entry may be kept even if they were
        // deactivated after it was posted (deactivation only blocks NEW use).
        Set<Long> keptThirdPartyIds = existingEntry.getItems().stream()
                .map(JournalEntryItem::getThirdParty)
                .filter(Objects::nonNull)
                .map(ThirdParty::getId)
                .collect(java.util.stream.Collectors.toSet());
        existingEntry.getItems().clear();
        processLineItems(existingEntry, request, companyId, keptThirdPartyIds);

        // 6. FINANCIAL VALIDATION -- same reasoning as create(): run on
        // the final, possibly tax-recalculated item amounts, after
        // processLineItems(), never on the client's raw request.
        validateBalance(existingEntry.getItems());

        // saveAndFlush (Bloque A): flushing here increments @Version now, so
        // the response carries the NEW version, not the one just replaced.
        JournalEntry saved = repository.saveAndFlush(existingEntry);
        log.info("Journal entry {} updated successfully", saved.getDocumentNumber());

        return mapToResponseDTO(saved);
    }

    // ═══════════════════════════════════════════════════════════
    // VALIDATION HELPERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Username (email) of the authenticated user, for the annulled_by audit
     * field. Same rule as AccountingPeriodController.currentUsername():
     * "system" only when there is no authenticated user.
     */
    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUsername();
        }
        return "system";
    }

    private void validateEntryDate(LocalDate date) {
        if (date == null) {
            throw new InvalidOperationException("Entry date is required", "ENTRY_DATE_REQUIRED");
        }

        if (date.isAfter(LocalDate.now())) {
            throw new InvalidOperationException("Entry date cannot be in the future", "ENTRY_DATE_IN_FUTURE");
        }

        // Deliberately no fixed "how far back" cutoff here. Catching up on a
        // prior fiscal year (e.g. finishing 2025 postings while 2026 is
        // already active) is normal, expected work, not an edge case. The
        // real control over how far back an entry may be dated is the
        // explicit period lock -- accountingPeriodService.validateDateIsOpen(),
        // invoked separately by create()/update() -- which lets each company
        // close a month/year once it's actually done, instead of an arbitrary
        // day-count that blocks legitimate backdated work.
        log.debug("Entry date validated: {}", date);
    }

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
                    String.format("Item #%d must have either debit or credit greater than zero.", itemIndex + 1)
            );
        }
    }

    private ChartOfAccounts fetchAndValidateAccount(Long accountId, Long companyId) {
        ChartOfAccounts account = accountRepository.findById(accountId)
                .filter(a -> a.getCompany().getId().equals(companyId))
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

    private void handleThirdParty(JournalEntryItem item, JournalEntryRequest.ItemRequest dto, ChartOfAccounts account, Long companyId,
                                  Set<Long> keptInactiveThirdPartyIds) {
        if (account.isRequiresThirdParty()) {
            if (dto.getThirdPartyId() == null) {
                throw new InvalidOperationException(
                        String.format("Account %s (%s) requires a Third Party to be specified.",
                                account.getCode(), account.getName())
                );
            }

            ThirdParty thirdParty = thirdPartyRepository.findById(dto.getThirdPartyId())
                    .filter(t -> t.getCompany().getId().equals(companyId))
                    .orElseThrow(() -> new ResourceNotFoundException("ThirdParty", dto.getThirdPartyId()));

            if (!Boolean.TRUE.equals(thirdParty.getActive())
                    && !keptInactiveThirdPartyIds.contains(thirdParty.getId())) {
                throw new InvalidOperationException(
                        String.format("Third Party %s is inactive and cannot be used.", thirdParty.getLegalDisplayName()),
                        "THIRD_PARTY_INACTIVE"
                );
            }

            item.setThirdParty(thirdParty);
        }
    }

    private void handleCostCenter(JournalEntryItem item, JournalEntryRequest.ItemRequest dto, ChartOfAccounts account, Long companyId) {
        if (account.isRequiresCostCenter()) {
            if (dto.getCostCenterId() == null) {
                throw new InvalidOperationException(
                        String.format("Account %s (%s) requires a Cost Center to be specified.",
                                account.getCode(), account.getName())
                );
            }

            CostCenter costCenter = costCenterRepository.findById(dto.getCostCenterId())
                    .filter(c -> c.getCompany().getId().equals(companyId))
                    .orElseThrow(() -> new ResourceNotFoundException("CostCenter", dto.getCostCenterId()));

            if (!costCenter.isActive()) {
                throw new InvalidOperationException(
                        String.format("Cost Center %s is inactive and cannot be used.", costCenter.getName())
                );
            }

            item.setCostCenter(costCenter);
        }
    }

    /**
     * Tax-linked accounts (2026-10-14, user-mandated design): if the item's
     * account is parameterized with a Tax (Tax.account, a one-to-one link
     * enforced by uk_tax_company_account), the client supplies only the
     * taxable base (taxBase) and the server derives the Tax and computes
     * the actual debit/credit from base x rate / 100, overwriting whatever
     * debit/credit the client sent for this line. This is the ONE
     * exception to "the client controls every line's amounts" -- required
     * for DIAN/Informacion Exogena consistency (see class javadoc).
     *
     * If the account is not tax-linked, this is a no-op and the item's
     * debit/credit stay exactly as the client submitted them.
     */
    private void handleTax(JournalEntryItem item, JournalEntryRequest.ItemRequest dto, ChartOfAccounts account, Long companyId) {
        Tax tax = taxRepository.findByCompanyIdAndAccount(companyId, account).orElse(null);
        if (tax == null) {
            return;
        }

        if (!tax.isActive()) {
            throw new InvalidOperationException(
                    String.format("Tax %s (%s) linked to account %s is inactive and cannot be used.",
                            tax.getCode(), tax.getName(), account.getCode())
            );
        }

        BigDecimal base = dto.getTaxBase();
        if (tax.isRequiresBase() && base == null) {
            throw new InvalidOperationException(
                    String.format("Account %s (%s) is linked to Tax %s, which requires a taxable base (Base Gravable) to be specified.",
                            account.getCode(), account.getName(), tax.getCode())
            );
        }
        if (base == null) {
            base = BigDecimal.ZERO;
        }

        // NEW (2026-10-15, found by a test exposing a real gap): a base
        // below the Tax's minimumBase cannot just compute to a $0
        // debit/credit -- every line in this service must carry exactly
        // one nonzero side (validateItemAmounts(), checked right after
        // this method returns), so silently zeroing both sides here
        // would only surface downstream as the generic, confusing "Item
        // #N must have either debit or credit greater than zero" error.
        // Reject it here instead, with a message that actually explains
        // why: below the minimum, this Tax simply doesn't apply, so the
        // line shouldn't be submitted at all.
        BigDecimal absBase = base.abs();
        if (tax.getMinimumBase() != null && absBase.compareTo(tax.getMinimumBase()) < 0) {
            throw new InvalidOperationException(
                    String.format("The base (%s) for account %s (%s) is below Tax %s's minimum base (%s) -- "
                                    + "no %s applies. Remove this line or enter a base that reaches the minimum.",
                            absBase.setScale(2, RoundingMode.HALF_UP),
                            account.getCode(), account.getName(), tax.getCode(),
                            tax.getMinimumBase().setScale(2, RoundingMode.HALF_UP),
                            tax.getCode())
            );
        }

        BigDecimal calculated = absBase.multiply(tax.getRate())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        if ("D".equalsIgnoreCase(tax.getSign())) {
            item.setDebit(calculated);
            item.setCredit(BigDecimal.ZERO);
        } else {
            item.setCredit(calculated);
            item.setDebit(BigDecimal.ZERO);
        }

        item.setTax(tax);
        item.setTaxBase(base);
    }

    // ═══════════════════════════════════════════════════════════
    // READ OPERATIONS
    // ═══════════════════════════════════════════════════════════

    public Page<JournalEntryResponseDTO> listEntries(String searchTerm, LocalDate start, LocalDate end, Long documentTypeId, Pageable pageable) {

        Long companyId = currentTenantId();
        log.debug("Listing journal entries for company ID: {}", companyId);

        Page<JournalEntryResponseDTO> page = repository
                .searchEntries(companyId, searchTerm, start, end, documentTypeId, pageable)
                .map(this::mapToResponseDTO);

        // Bloque A (2026-11-21): how many saved versions each entry of the
        // page has, in ONE query, for the history icon in the list.
        Map<Long, Long> counts = historyService.countByEntry(companyId,
                page.getContent().stream().map(JournalEntryResponseDTO::getId).toList());
        page.getContent().forEach(dto -> dto.setHistoryCount(counts.getOrDefault(dto.getId(), 0L)));
        return page;
    }

    public JournalEntryResponseDTO findByDocumentNumber(String docNum) {
        Long companyId = currentTenantId();
        return repository.findByCompanyIdAndDocumentNumber(companyId, docNum)
                .map(this::mapToResponseDTO)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Journal entry with document number '" + docNum + "' not found"
                ));
    }

    /**
     * Bloque A (2026-11-21): saved versions of an entry (newest first), each
     * one the full entry as it was before an edit or the annulment.
     */
    public List<JournalEntryHistoryDTO> getHistory(Long id) {
        Long companyId = currentTenantId();
        repository.findById(id)
                .filter(e -> e.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Journal Entry", id));
        return historyService.list(companyId, id);
    }

    public JournalEntryResponseDTO findById(Long id) {
        Long companyId = currentTenantId();
        JournalEntry entry = repository.findById(id)
                .filter(e -> e.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Journal Entry", id));
        JournalEntryResponseDTO dto = mapToResponseDTO(entry);
        // NEW (2026-10-22, user-reported UX issue): the frontend needs to
        // know up front whether this entry's period is closed, so it can
        // open the form read-only instead of letting the user type a full
        // edit that only fails -- with everything typed lost -- once they
        // click Guardar and hit accountingPeriodService.validateDateIsOpen()'s
        // rejection. Scoped to this single-entry fetch only; the shared
        // mapToResponseDTO() used by listEntries() is left untouched so the
        // list screen doesn't pay an extra isPeriodClosed() query per row.
        LocalDate entryDate = entry.getEntryDate();
        dto.setPeriodClosed(accountingPeriodService.isPeriodClosed(entryDate.getYear(), entryDate.getMonthValue()));
        dto.setHistoryCount(historyService.count(companyId, id));
        return dto;
    }
    // ═══════════════════════════════════════════════════════════
    // TRIAL BALANCE REPORTS
    // ═══════════════════════════════════════════════════════════

    public TrialBalanceReport getTrialBalanceReport(LocalDate asOfDate) {
        Long companyId = currentTenantId();
        if (asOfDate == null) {
            asOfDate = LocalDate.now();
        }

        log.info("Generating Trial Balance for company ID: {} as of {}", companyId, asOfDate);

        List<TrialBalanceLine> lines = accountRepository.getTrialBalance(companyId, asOfDate);

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (TrialBalanceLine line : lines) {
            totalDebit = totalDebit.add(line.getTotalDebit());
            totalCredit = totalCredit.add(line.getTotalCredit());
        }

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

        if (invalidCount > 0) {
            log.warn("Found {} trial balance lines with null net balance", invalidCount);
        }

        boolean isBalanced = totalDebit.setScale(2, RoundingMode.HALF_UP)
                .compareTo(totalCredit.setScale(2, RoundingMode.HALF_UP)) == 0;

        if (!isBalanced) {
            log.warn("TRIAL BALANCE OUT OF BALANCE! Debit: {}, Credit: {}, Difference: {}",
                    totalDebit, totalCredit, totalDebit.subtract(totalCredit));
        }

        return TrialBalanceReport.builder()
                .companyName(companyRepository.getReferenceById(companyId).getLegalName())
                .asOfDate(asOfDate)
                .generatedAt(LocalDateTime.now())
                .lines(lines)
                .totalDebit(totalDebit)
                .totalCredit(totalCredit)
                .balanced(isBalanced)
                .summary(summary)
                .build();
    }

    /**
     * "Balance de Prueba" / detailed Trial Balance, for a date range.
     *
     * NEW (2026-09-29): rebuilt to show the FULL chart of accounts
     * hierarchy (class, group, subgroup, account -- every level, not
     * just the leaf/posting accounts that actually received postings),
     * each with its own opening balance + period movement + closing
     * balance, matching the user's legacy "Balance de Prueba" report
     * exactly (requested 2026-09-28, confirmed 2026-09-29 after
     * comparing this report specifically -- not the plain Trial Balance
     * -- against a screenshot of that legacy screen). A header
     * account's totals are a pure rollup of its descendants: the
     * business rule enforced in ChartOfAccountService (a parent with
     * children can never itself be a posting account) means only leaves
     * ever carry a direct opening balance or period activity, so
     * walking up from each leaf and adding into every ancestor, once,
     * is enough -- no risk of double-counting a header's own postings,
     * because headers never have any.
     *
     * The grand total and per-class summary still only ever count each
     * LEAF once (see the `postingAccount` guard below) -- summing every
     * line in the now-hierarchical `lines` list would double-count
     * everything, since a header line's own numbers already are the sum
     * of its descendants.
     */
    public TrialBalanceReportDetailed getTrialBalanceDetailed(LocalDate startDate, LocalDate endDate) {
        // CORREGIDO: Usamos el método unificado del Tenant en lugar de getCompanyId()
        Long companyId = currentTenantId();

        if (startDate == null || endDate == null) {
            throw new InvalidOperationException("Start date and end date are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new InvalidOperationException("Start date cannot be after end date");
        }

        log.info("Generating detailed Trial Balance for company ID: {} from {} to {}", companyId, startDate, endDate);

        Map<String, BigDecimal> openingBalances = new HashMap<>();
        List<Object[]> openings = accountRepository.getOpeningBalances(companyId, startDate);

        for (Object[] row : openings) {
            String code = (String) row[0];
            BigDecimal balance = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
            openingBalances.put(code, balance.setScale(2, RoundingMode.HALF_UP));
        }

        // Indexed by code instead of iterated directly (as this used to
        // do) -- the rollup below needs random access to a leaf's own
        // activity while walking up its ancestor chain.
        Map<String, BigDecimal[]> periodActivityByCode = new HashMap<>();
        List<Object[]> activities = accountRepository.getPeriodActivity(companyId, startDate, endDate);
        for (Object[] row : activities) {
            String code = (String) row[0];
            BigDecimal periodDebit = row[4] != null ? (BigDecimal) row[4] : BigDecimal.ZERO;
            BigDecimal periodCredit = row[5] != null ? (BigDecimal) row[5] : BigDecimal.ZERO;
            periodActivityByCode.put(code, new BigDecimal[]{
                    periodDebit.setScale(2, RoundingMode.HALF_UP),
                    periodCredit.setScale(2, RoundingMode.HALF_UP)
            });
        }

        // Full active chart (not just posting accounts) -- header
        // accounts never appear in the two queries above (they only
        // ever query JournalEntryItem, which only posting accounts
        // receive), but they still need a row here, with a rolled-up
        // subtotal. Already ordered by code, which -- since every PUC
        // code is a literal string prefix of its children's codes -- is
        // already a valid pre-order tree traversal, so `lines` below
        // comes out correctly nested with no extra sorting.
        List<ChartOfAccounts> allAccounts = accountRepository.findByCompanyIdAndActiveTrueOrderByCodeAsc(companyId);

        Map<String, BigDecimal[]> rolledUp = new HashMap<>();
        for (ChartOfAccounts account : allAccounts) {
            rolledUp.put(account.getCode(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
        }
        for (ChartOfAccounts account : allAccounts) {
            if (!account.isPostingAccount()) {
                continue; // only leaves ever have direct activity to roll up
            }

            BigDecimal opening = account.isClosesAtYearEnd()
                    ? BigDecimal.ZERO
                    : openingBalances.getOrDefault(account.getCode(), BigDecimal.ZERO);
            BigDecimal[] activity = periodActivityByCode.getOrDefault(
                    account.getCode(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});

            ChartOfAccounts current = account;
            while (current != null) {
                BigDecimal[] totals = rolledUp.get(current.getCode());
                totals[0] = totals[0].add(opening);
                totals[1] = totals[1].add(activity[0]);
                totals[2] = totals[2].add(activity[1]);
                current = current.getParent();
            }
        }

        List<TrialBalanceLineDetailed> lines = new ArrayList<>();
        BigDecimal totalOpeningBalance = BigDecimal.ZERO;
        BigDecimal totalPeriodDebit = BigDecimal.ZERO;
        BigDecimal totalPeriodCredit = BigDecimal.ZERO;
        BigDecimal totalNetMovement = BigDecimal.ZERO;
        BigDecimal totalClosingBalance = BigDecimal.ZERO;

        Map<String, BigDecimal> summaryByClass = new HashMap<>();

        for (ChartOfAccounts account : allAccounts) {
            BigDecimal[] totals = rolledUp.get(account.getCode());
            BigDecimal opening = totals[0].setScale(2, RoundingMode.HALF_UP);
            BigDecimal periodDebit = totals[1].setScale(2, RoundingMode.HALF_UP);
            BigDecimal periodCredit = totals[2].setScale(2, RoundingMode.HALF_UP);
            BigDecimal netMovement = periodDebit.subtract(periodCredit);
            BigDecimal closing = opening.add(netMovement);

            // Same "hide if there's genuinely nothing to show" rule
            // already used by the Cost Center and Third Party reports --
            // otherwise an unused branch of a freshly-seeded PUC template
            // would flood this report with hundreds of all-zero rows. A
            // header's totals are a pure rollup (see above), so if every
            // descendant is zero, the header is zero too and gets
            // skipped right along with them.
            boolean allZero = opening.compareTo(BigDecimal.ZERO) == 0
                    && periodDebit.compareTo(BigDecimal.ZERO) == 0
                    && periodCredit.compareTo(BigDecimal.ZERO) == 0
                    && closing.compareTo(BigDecimal.ZERO) == 0;
            if (allZero) {
                continue;
            }

            String classDisplay = getAccountClassDisplay(account.getAccountClass());

            lines.add(TrialBalanceLineDetailed.builder()
                    .accountCode(account.getCode())
                    .accountName(account.getName())
                    .accountClass(classDisplay)
                    .balanceSheetAccount(!account.isClosesAtYearEnd())
                    .level(account.getLevel() != null ? account.getLevel().intValue() : 1)
                    .postingAccount(account.isPostingAccount())
                    .openingBalance(opening)
                    .periodDebit(periodDebit)
                    .periodCredit(periodCredit)
                    .netMovement(netMovement)
                    .closingBalance(closing)
                    .build());

            // Grand total and per-class summary count each LEAF only --
            // see the method Javadoc for why counting header rows too
            // would double-count everything.
            if (account.isPostingAccount()) {
                totalOpeningBalance = totalOpeningBalance.add(opening);
                totalPeriodDebit = totalPeriodDebit.add(periodDebit);
                totalPeriodCredit = totalPeriodCredit.add(periodCredit);
                totalNetMovement = totalNetMovement.add(netMovement);
                totalClosingBalance = totalClosingBalance.add(closing);
                summaryByClass.merge(classDisplay, closing, BigDecimal::add);
            }
        }

        boolean isBalanced = totalPeriodDebit.setScale(2, RoundingMode.HALF_UP)
                .compareTo(totalPeriodCredit.setScale(2, RoundingMode.HALF_UP)) == 0;

        if (!isBalanced) {
            log.warn("DETAILED TRIAL BALANCE OUT OF BALANCE! Period Debit: {}, Period Credit: {}, Difference: {}",
                    totalPeriodDebit, totalPeriodCredit, totalPeriodDebit.subtract(totalPeriodCredit));
        }

        return TrialBalanceReportDetailed.builder()
                .companyName(companyRepository.getReferenceById(companyId).getLegalName())
                .startDate(startDate)
                .endDate(endDate)
                .generatedAt(LocalDateTime.now())
                .lines(lines)
                .totalOpeningBalance(totalOpeningBalance.setScale(2, RoundingMode.HALF_UP))
                .totalPeriodDebit(totalPeriodDebit.setScale(2, RoundingMode.HALF_UP))
                .totalPeriodCredit(totalPeriodCredit.setScale(2, RoundingMode.HALF_UP))
                .totalNetMovement(totalNetMovement.setScale(2, RoundingMode.HALF_UP))
                .totalClosingBalance(totalClosingBalance.setScale(2, RoundingMode.HALF_UP))
                .balanced(isBalanced)
                .summaryByClass(summaryByClass)
                .build();
    }

    // NOTE (2026-09-08): these used to return hardcoded English display
    // strings (e.g. "1 - Assets") that went straight into the summary Map
    // key -- fine as long as nothing rendered them, but the moment the
    // frontend put "Resumen por Clase" on screen, a Spanish-language user
    // saw "1 - Assets" with no way to translate it (the key WAS the
    // display text). Returning a bare, language-neutral code instead lets
    // the frontend translate it for display, same as every other
    // backend-owned code in this app (see apiErrors.js on the frontend).
    private String getAccountClassDisplay(AccountClass accountClass) {
        return switch (accountClass) {
            case ASSET -> "1";
            case LIABILITY -> "2";
            case EQUITY -> "3";
            case REVENUE -> "4";
            case EXPENSE -> "5";
            case COST -> "6-7";
        };
    }

    private String getAccountClassName(String code) {
        if (code == null || code.isEmpty()) {
            return "OTHER";
        }
        char firstDigit = code.charAt(0);
        return switch (firstDigit) {
            case '1' -> "1";
            case '2' -> "2";
            case '3' -> "3";
            case '4' -> "4";
            case '5' -> "5";
            case '6', '7' -> "6-7";
            default -> "OTHER";
        };
    }

    // ═══════════════════════════════════════════════════════════
    // DTO MAPPING
    // ═══════════════════════════════════════════════════════════

    private JournalEntryResponseDTO mapToResponseDTO(JournalEntry entry) {
        if (entry == null) {
            return null;
        }

        JournalEntryResponseDTO dto = new JournalEntryResponseDTO();
        dto.setId(entry.getId());
        dto.setDocumentNumber(entry.getDocumentNumber());
        dto.setConsecutive(entry.getConsecutive());
        dto.setDocumentTypeId(entry.getDocumentType().getId());
        dto.setEntryDate(entry.getEntryDate());
        dto.setDescription(entry.getDescription());
        // BUG FIX: these three fields exist on the DTO specifically so the
        // frontend can show annulment status, but were never populated here
        // -- every response (create/annul/update/find/list) silently
        // reported annulled=false regardless of the entry's real state.
        dto.setAnnulled(entry.isAnnulled());
        dto.setAnnulledAt(entry.getAnnulledAt());
        dto.setAnnulmentReason(entry.getAnnulmentReason());
        dto.setVersion(entry.getVersion());
        // Bloque A fase 3 (2026-11-24): link between an annulled original and
        // its ANULACION reverse, both ways.
        if (entry.getReversalEntry() != null) {
            dto.setReversalEntryId(entry.getReversalEntry().getId());
            dto.setReversalDocumentNumber(entry.getReversalEntry().getDocumentNumber());
        }
        if (entry.getReversalOf() != null) {
            dto.setReversalOfEntryId(entry.getReversalOf().getId());
            dto.setReversalOfDocumentNumber(entry.getReversalOf().getDocumentNumber());
        }

        List<JournalEntryResponseDTO.ItemResponse> items = entry.getItems().stream()
                .map(item -> {
                    JournalEntryResponseDTO.ItemResponse i = new JournalEntryResponseDTO.ItemResponse();
                    i.setId(item.getId());
                    i.setAccountId(item.getAccount().getId());
                    i.setAccountCode(item.getAccount().getCode());
                    i.setAccountName(item.getAccount().getName());
                    i.setDebit(item.getDebit());
                    i.setCredit(item.getCredit());
                    i.setDescription(item.getDescription());

                    if (item.getThirdParty() != null) {
                        i.setThirdPartyId(item.getThirdParty().getId());
                        i.setThirdPartyIdNumber(item.getThirdParty().getDocumentNumber());
                        i.setThirdPartyName(item.getThirdParty().getLegalDisplayName());
                    }
                    if (item.getCostCenter() != null) {
                        i.setCostCenterId(item.getCostCenter().getId());
                        i.setCostCenterName(item.getCostCenter().getName());
                    }
                    if (item.getTax() != null) {
                        i.setTaxId(item.getTax().getId());
                        i.setTaxCode(item.getTax().getCode());
                        i.setTaxName(item.getTax().getName());
                        // NEW (2026-10-15): type is free text now, not
                        // an enum -- see TaxCategory's javadoc for why.
                        i.setTaxType(item.getTax().getType());
                        i.setTaxCategory(item.getTax().getCategory().name());
                        i.setTaxRate(item.getTax().getRate());
                        i.setTaxBase(item.getTaxBase());
                    }
                    return i;
                })
                .toList();

        dto.setItems(items);
        return dto;
    }
}