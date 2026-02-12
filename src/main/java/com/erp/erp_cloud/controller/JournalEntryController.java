package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.JournalEntryRequest;
import com.erp.erp_cloud.dto.JournalEntryResponseDTO;

import com.erp.erp_cloud.service.JournalEntryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/journal-entries")
@RequiredArgsConstructor
public class JournalEntryController {

    private final JournalEntryService service;

    /**
     * Creates a new accounting voucher (Journal Entry).
     *
     * @param request The DTO containing the header and items.
     * @return The created JournalEntry with its assigned consecutive and ID.
     */
    @PostMapping

    public ResponseEntity<JournalEntryResponseDTO> create(@Valid @RequestBody JournalEntryRequest request) {
        JournalEntryResponseDTO response = service.create(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }


    @GetMapping
    public ResponseEntity<Page<JournalEntryResponseDTO>> getAll(
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable) {

        return ResponseEntity.ok(service.listEntries(searchTerm, startDate, endDate, pageable));
    }
}