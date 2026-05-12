package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.AccountingPeriodActionRequest;
import com.erp.erp_cloud.dto.AccountingPeriodRequest;
import com.erp.erp_cloud.dto.AccountingPeriodResponseDTO;
import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.service.AccountingPeriodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounting-periods")
@RequiredArgsConstructor
@Tag(name = "Accounting Periods", description = "Endpoints for managing fiscal periods, including closing and reopening months")
public class AccountingPeriodController {

    private final AccountingPeriodService service;

    /**
     * Gets all accounting periods for the current company.
     */
    @GetMapping
    @Operation(summary = "Get all periods", description = "Retrieves all accounting periods configured for the current company.")
    public ResponseEntity<ApiResponse<List<AccountingPeriodResponseDTO>>> getAllPeriods() {
        // Standardized to findByCompany context
        List<AccountingPeriodResponseDTO> periods = service.findAllByCompany();
        return ResponseEntity.ok(new ApiResponse<>("Periods retrieved successfully", true, periods));
    }

    /**
     * Gets all closed periods for the current company.
     */
    @GetMapping("/closed")
    @Operation(summary = "Get closed periods")
    public ResponseEntity<ApiResponse<List<AccountingPeriodResponseDTO>>> getClosedPeriods() {
        List<AccountingPeriodResponseDTO> periods = service.findClosedByCompany();
        return ResponseEntity.ok(new ApiResponse<>("Closed periods retrieved", true, periods));
    }

    /**
     * Gets all open periods for the current company.
     */
    @GetMapping("/open")
    @Operation(summary = "Get open periods")
    public ResponseEntity<ApiResponse<List<AccountingPeriodResponseDTO>>> getOpenPeriods() {
        List<AccountingPeriodResponseDTO> periods = service.findOpenByCompany();
        return ResponseEntity.ok(new ApiResponse<>("Open periods retrieved", true, periods));
    }

    /**
     * Gets a specific accounting period.
     */
    @GetMapping("/{year}/{month}")
    @Operation(summary = "Get specific period")
    public ResponseEntity<ApiResponse<AccountingPeriodResponseDTO>> getPeriod(
            @PathVariable Integer year,
            @PathVariable Integer month) {

        // Using findByYearAndMonth which returns Optional
        AccountingPeriodResponseDTO period = service.findByYearAndMonth(year, month)
                .orElseThrow(() -> new InvalidOperationException(
                        String.format("Period %d-%02d not found", year, month)
                ));

        return ResponseEntity.ok(new ApiResponse<>("Period retrieved", true, period));
    }

    /**
     * Closes an accounting period (Monthly close).
     */
    @PostMapping("/{year}/{month}/close")
    @Operation(summary = "Close accounting period")
    public ResponseEntity<ApiResponse<AccountingPeriodResponseDTO>> closePeriod(
            @PathVariable Integer year,
            @PathVariable Integer month,
            @Valid @RequestBody AccountingPeriodActionRequest request) { // Use the new ActionRequest

        String currentUser = "system";
        AccountingPeriodResponseDTO period = service.closePeriod(year, month, currentUser, request.getNotes());
        return ResponseEntity.ok(new ApiResponse<>("Period closed", true, period));
    }

    /**
     * Closes a full fiscal year (Annual close).
     */
    @PostMapping("/{year}/close-year")
    @Operation(summary = "Close fiscal year")
    public ResponseEntity<ApiResponse<Void>> closeYear(
            @PathVariable Integer year,
            @Valid @RequestBody AccountingPeriodActionRequest request) { // Consistent UI

        String currentUser = "system";
        service.closeYear(year, currentUser, request.getNotes());
        return ResponseEntity.ok(new ApiResponse<>("Fiscal year locked", true, null));
    }

    /**
     * Reopens a closed accounting period.
     */
    @PostMapping("/{year}/{month}/reopen")
    @Operation(summary = "Reopen closed period")
    public ResponseEntity<ApiResponse<AccountingPeriodResponseDTO>> reopenPeriod(
            @PathVariable Integer year,
            @PathVariable Integer month,
            @Valid @RequestBody AccountingPeriodRequest request) {

        if (request.getNotes() == null || request.getNotes().trim().isEmpty()) {
            throw new InvalidOperationException("Notes are required when reopening a period");
        }

        String currentUser = "system";
        AccountingPeriodResponseDTO period = service.reopenPeriod(year, month, currentUser, request.getNotes());

        return ResponseEntity.ok(new ApiResponse<>(
                String.format("Period %d-%02d reopened", year, month),
                true,
                period
        ));
    }

    /**
     * Reopens a closed fiscal year.
     */
    @PostMapping("/{year}/reopen-year")
    @Operation(summary = "Reopen fiscal year", description = "Removes the annual seal. Individual closed months will remain closed.")
    public ResponseEntity<ApiResponse<Void>> reopenYear(
            @PathVariable Integer year,
            @Valid @RequestBody AccountingPeriodActionRequest request) {

        String currentUser = "system"; // TODO: SecurityContext
        service.reopenYear(year, currentUser, request.getNotes());

        return ResponseEntity.ok(new ApiResponse<>(
                String.format("Fiscal year %d unsealed successfully", year),
                true,
                null
        ));
    }


}