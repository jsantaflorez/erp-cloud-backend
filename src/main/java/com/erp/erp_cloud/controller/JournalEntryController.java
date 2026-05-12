package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.*;
import com.erp.erp_cloud.service.JournalEntryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

import java.time.LocalDate;

/**
 * REST Controller for Journal Entries (Comprobantes Contables).
 *
 * Current Version: Manual Entry Only
 * - All entries require manual balancing (debits = credits)
 * - No automatic tax calculation
 * - Full accountant control
 *
 * Future versions will support automated tax calculation.
 */
@RestController
@RequestMapping("/api/v1/journal-entries")
@RequiredArgsConstructor
@Tag(name = "Journal Entries", description = "Endpoints for managing accounting vouchers (Comprobantes Contables)")
public class JournalEntryController {

    private final JournalEntryService service;

    // ═══════════════════════════════════════════════════════════
    // CREATE OPERATIONS
    // ═══════════════════════════════════════════════════════════

    /**
     * Creates a new journal entry with strict validation.
     *
     * Business Rules (Version 1.0):
     * - Entry MUST be perfectly balanced (total debits = total credits)
     * - NO automatic tax calculation (taxes must be entered manually)
     * - NO automatic balancing adjustments
     * - Accounting period must be open for the entry date
     *
     * Request Body Example:
     * {
     *   "documentTypeId": 1,
     *   "entryDate": "2026-05-01",
     *   "description": "Venta al contado con IVA",
     *   "items": [
     *     {
     *       "accountId": 4,
     *       "debit": 1190.00,
     *       "credit": 0.00,
     *       "description": "Efectivo recibido"
     *     },
     *     {
     *       "accountId": 27,
     *       "debit": 0.00,
     *       "credit": 1000.00,
     *       "description": "Ingreso por venta"
     *     },
     *     {
     *       "accountId": 10,
     *       "debit": 0.00,
     *       "credit": 190.00,
     *       "description": "IVA generado 19%"
     *     }
     *   ]
     * }
     *
     * @param request Journal entry data
     * @return Created journal entry with generated document number
     */
    @PostMapping
    @Operation(
            summary = "Create journal entry",
            description = "Creates a new accounting voucher with strict manual validation. " +
                    "Entry must be perfectly balanced (debits = credits). " +
                    "Taxes must be entered manually (no automatic calculation)."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Journal entry created successfully"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Validation error: Entry is unbalanced, invalid date, or period is closed"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Referenced entity not found (account, document type, third party, or cost center)"
    )
    public ResponseEntity<ApiResponse<JournalEntryResponseDTO>> create(
            @Valid @RequestBody JournalEntryRequest request) {

        JournalEntryResponseDTO created = service.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>(
                        "Journal entry created successfully: " + created.getDocumentNumber(),
                        true,
                        created
                ));
    }

    // ═══════════════════════════════════════════════════════════
    // UPDATE & STATE OPERATIONS
    // ═══════════════════════════════════════════════════════════

    /**
     * Updates an existing journal entry.
     * * Business Rules:
     * - Both current and new entry dates must be in OPEN accounting periods.
     * - The entry must be perfectly balanced after the update.
     * - All item validations (accounts, third parties, cost centers) are re-evaluated.
     * * @param id Internal database ID of the entry to update
     * @param request Updated journal entry data
     * @return Updated journal entry details
     */
    @PutMapping("/{id}")
    @Operation(
            summary = "Update journal entry",
            description = "Updates an existing accounting voucher. " +
                    "Validation ensures that neither the old date nor the new date belongs to a closed period. " +
                    "The entry must remain balanced (debits = credits)."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Journal entry updated successfully"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Validation error: Unbalanced entry or attempting to modify a closed period"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Journal entry, account, or referenced entity not found"
    )
    public ResponseEntity<ApiResponse<JournalEntryResponseDTO>> update(
            @PathVariable Long id,
            @Valid @RequestBody JournalEntryRequest request) {

        JournalEntryResponseDTO updated = service.update(id, request);

        return ResponseEntity.ok(new ApiResponse<>(
                "Journal entry updated successfully: " + updated.getDocumentNumber(),
                true,
                updated
        ));
    }

    // ═══════════════════════════════════════════════════════════
    // READ OPERATIONS
    // ═══════════════════════════════════════════════════════════

    /**
     * Lists all journal entries with optional filtering and pagination.
     *
     * Query Parameters:
     * - searchTerm: Searches in document number and description (optional)
     * - startDate: Filter entries from this date onwards (optional)
     * - endDate: Filter entries up to this date (optional)
     * - page: Page number (0-based, default: 0)
     * - size: Page size (default: 20)
     * - sort: Sort criteria (e.g., "entryDate,desc")
     *
     * Examples:
     * - GET /api/v1/journal-entries?page=0&size=20
     * - GET /api/v1/journal-entries?searchTerm=RC-001
     * - GET /api/v1/journal-entries?startDate=2026-01-01&endDate=2026-12-31
     * - GET /api/v1/journal-entries?startDate=2026-05-01&sort=entryDate,desc
     *
     * @param searchTerm Optional search term
     * @param startDate Optional start date filter
     * @param endDate Optional end date filter
     * @param pageable Pagination parameters
     * @return Paginated list of journal entries
     */
    @GetMapping
    @Operation(
            summary = "List journal entries",
            description = "Retrieves journal entries with support for pagination, date range filters, and search. " +
                    "Search works on document number and description fields."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Entries retrieved successfully"
    )
    public ResponseEntity<ApiResponse<Page<JournalEntryResponseDTO>>> list(
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @ParameterObject Pageable pageable) {

        Page<JournalEntryResponseDTO> data = service.listEntries(
                searchTerm, startDate, endDate, pageable
        );

        return ResponseEntity.ok(new ApiResponse<>(
                String.format("Retrieved %d journal entries (page %d of %d)",
                        data.getNumberOfElements(),
                        data.getNumber() + 1,
                        data.getTotalPages()),
                true,
                data
        ));
    }

    /**
     * Retrieves a journal entry by its document number.
     *
     * Document numbers are business-friendly identifiers like:
     * - "RC-001" (Recibo de Caja 001)
     * - "EG-0045" (Egreso 0045)
     * - "CE-2056" (Comprobante de Egreso 2056)
     *
     * This is useful for searching entries by their human-readable reference.
     *
     * @param documentNumber Business document number (e.g., "RC-001")
     * @return Journal entry details
     */
    @GetMapping("/by-number/{documentNumber}")
    @Operation(
            summary = "Get entry by document number",
            description = "Retrieves a journal entry using its business reference number " +
                    "(e.g., RC-001, EG-0045). This is the human-readable identifier " +
                    "printed on physical vouchers."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Entry found"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Document number not found"
    )
    public ResponseEntity<ApiResponse<JournalEntryResponseDTO>> getByDocumentNumber(
            @PathVariable String documentNumber) {

        JournalEntryResponseDTO data = service.findByDocumentNumber(documentNumber);

        return ResponseEntity.ok(new ApiResponse<>(
                "Journal entry found: " + documentNumber,
                true,
                data
        ));
    }

    /**
     * Retrieves a journal entry by its internal database ID.
     *
     * This is typically used by the frontend for:
     * - View Details page
     * - Edit functionality
     * - Internal references
     *
     * Note: Use getByDocumentNumber for user-facing searches.
     *
     * @param id Database primary key
     * @return Journal entry details
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Get entry by ID",
            description = "Retrieves a specific journal entry by its internal database ID. " +
                    "Used for detail views and editing. Validates that the entry " +
                    "belongs to the current user's company."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Journal entry retrieved successfully"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Journal entry not found or belongs to another company"
    )
    public ResponseEntity<ApiResponse<JournalEntryResponseDTO>> getById(@PathVariable Long id) {
        JournalEntryResponseDTO data = service.findById(id);

        return ResponseEntity.ok(new ApiResponse<>(
                "Journal entry retrieved successfully",
                true,
                data
        ));
    }

    // Dentro de JournalEntryController.java

    /**
     * Annuls a journal entry.
     * Annulling is the professional way to "cancel" a voucher without deleting it.
     * The document keeps its number but its financial effect is neutralized.
     */
    @PatchMapping("/{id}/annul")
    @Operation(
            summary = "Annul journal entry",
            description = "Neutralizes a journal entry's financial impact. " +
                    "The entry remains in the database for audit trail but is marked as annulled. " +
                    "Cannot annul entries in closed accounting periods."+
                     "Requires a reason for audit purposes."
       )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Journal entry annulled successfully"
    )
    public ResponseEntity<ApiResponse<JournalEntryResponseDTO>> annul(
            @PathVariable Long id,
            @Valid @RequestBody JournalEntryRequest.AnnulmentRequest request) {

        JournalEntryResponseDTO annulled = service.annul(id, request);

        return ResponseEntity.ok(new ApiResponse<>(
                "Journal entry annulled successfully: " + annulled.getDocumentNumber(),
                true,
                annulled
        ));
    }



}