package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.*;

import com.erp.erp_cloud.service.JournalEntryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;


import java.time.LocalDate;
@RestController
@RequestMapping("/api/v1/journal-entries")
@RequiredArgsConstructor
@Tag(name = "Journal Entries", description = "Endpoints for managing accounting vouchers") // Para Swagger/OpenAPI
public class JournalEntryController {

    private final JournalEntryService service;

    /**
     * STANDARD CREATE: Used for automated processes (Invoices, Payments).
     * Includes auto-balancing and system tax calculations.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<JournalEntryResponseDTO>> create(@Valid @RequestBody JournalEntryRequest request) {
        JournalEntryResponseDTO created = service.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>("Journal Entry created successfully", true, created));
    }

    /**
     * MANUAL VOUCHER: Specific endpoint for accountants.
     * Disables auto-balancing to ensure strict manual integrity.
     */
    @PostMapping("/manual")
    public ResponseEntity<ApiResponse<JournalEntryResponseDTO>> createManual(@Valid @RequestBody JournalEntryRequest request) {
        // Calling the specific manual logic we just implemented
        JournalEntryResponseDTO created = service.createManualVoucher(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>("Manual Voucher created successfully", true, created));
    }

    /**
     * SEARCH & LIST: Optimized with pagination and date filters.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<JournalEntryResponseDTO>>> getAll(
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable) {

        Page<JournalEntryResponseDTO> data = service.listEntries(searchTerm, startDate, endDate, pageable);
        return ResponseEntity.ok(new ApiResponse<>("Journal entries retrieved successfully", true, data));
    }

    /**
     * GET BY DOCUMENT NUMBER: Useful for searching "CC-10" or "FV-500".
     */
    @GetMapping("/by-number/{documentNumber}")
    public ResponseEntity<ApiResponse<JournalEntryResponseDTO>> getByDocumentNumber(@PathVariable String documentNumber) {
        JournalEntryResponseDTO data = service.findByDocumentNumber(documentNumber);
        return ResponseEntity.ok(new ApiResponse<>("Entry found", true, data));
    }
    /**
     * GET BY ID: Essential for the frontend "View Details" or "Edit" pages.
     * Use this when you have the internal database ID.
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Retrieve a journal entry by ID",
            description = "Fetches a specific accounting voucher and validates ownership for the current company."
    )
    public ResponseEntity<ApiResponse<JournalEntryResponseDTO>> getById(@PathVariable Long id) {
        JournalEntryResponseDTO data = service.findById(id);
        return ResponseEntity.ok(new ApiResponse<>("Journal entry retrieved", true, data));
    }
}