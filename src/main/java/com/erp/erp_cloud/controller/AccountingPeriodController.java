package com.erp.erp_cloud.controller;

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
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Periods retrieved successfully")
    public ResponseEntity<ApiResponse<List<AccountingPeriodResponseDTO>>> getAllPeriods() {
        List<AccountingPeriodResponseDTO> periods = service.getAllPeriods();
        return ResponseEntity.ok(new ApiResponse<>("Periods retrieved successfully", true, periods));
    }

    /**
     * Gets all closed periods for the current company.
     */
    @GetMapping("/closed")
    @Operation(summary = "Get closed periods", description = "Retrieves only the periods that are currently in 'CLOSED' status.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Closed periods retrieved")
    public ResponseEntity<ApiResponse<List<AccountingPeriodResponseDTO>>> getClosedPeriods() {
        List<AccountingPeriodResponseDTO> periods = service.getClosedPeriods();
        return ResponseEntity.ok(new ApiResponse<>("Closed periods retrieved", true, periods));
    }

    /**
     * Gets all open periods for the current company.
     */
    @GetMapping("/open")
    @Operation(summary = "Get open periods", description = "Retrieves only the periods that are available for posting transactions.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Open periods retrieved")
    public ResponseEntity<ApiResponse<List<AccountingPeriodResponseDTO>>> getOpenPeriods() {
        List<AccountingPeriodResponseDTO> periods = service.getOpenPeriods();
        return ResponseEntity.ok(new ApiResponse<>("Open periods retrieved", true, periods));
    }

    /**
     * Gets a specific accounting period.
     */
    @GetMapping("/{year}/{month}")
    @Operation(summary = "Get specific period", description = "Fetches details for a specific month and year.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Period found")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Period not found")
    public ResponseEntity<ApiResponse<AccountingPeriodResponseDTO>> getPeriod(
            @PathVariable Integer year,
            @PathVariable Integer month) {

        AccountingPeriodResponseDTO period = service.getPeriod(year, month)
                .orElseThrow(() -> new InvalidOperationException(
                        String.format("Period %d-%02d not found", year, month)
                ));

        return ResponseEntity.ok(new ApiResponse<>("Period retrieved", true, period));
    }

    /**
     * Closes an accounting period.
     *
     * TODO: Add security - only ADMIN or ACCOUNTANT roles should be allowed
     */
    @PostMapping("/{year}/{month}/close")
    @Operation(summary = "Close accounting period", description = "Finalizes the period. No further entries can be posted once closed.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Period closed successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid operation or period already closed")
    public ResponseEntity<ApiResponse<AccountingPeriodResponseDTO>> closePeriod(
            @PathVariable Integer year,
            @PathVariable Integer month,
            @Valid @RequestBody AccountingPeriodRequest request) {

        // TODO: Get current user from security context when implemented
        String currentUser = "system"; // Temporary placeholder

        AccountingPeriodResponseDTO period = service.closePeriod(year, month, currentUser, request.getNotes());

        return ResponseEntity.ok(new ApiResponse<>(
                String.format("Period %d-%02d closed successfully", year, month),
                true,
                period
        ));
    }

    /**
     * Reopens a closed accounting period.
     *
     * TODO: Add security - only ADMIN role should be allowed
     */
    @PostMapping("/{year}/{month}/reopen")
    @Operation(summary = "Reopen closed period", description = "Allows posting entries again. Requires audit notes and administrative privileges.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Period reopened successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Notes required or invalid period state")
    public ResponseEntity<ApiResponse<AccountingPeriodResponseDTO>> reopenPeriod(
            @PathVariable Integer year,
            @PathVariable Integer month,
            @Valid @RequestBody AccountingPeriodRequest request) {

        // Validate notes are provided
        if (request.getNotes() == null || request.getNotes().trim().isEmpty()) {
            throw new InvalidOperationException("Notes are required when reopening a period");
        }

        // TODO: Get current user from security context when implemented
        String currentUser = "system"; // Temporary placeholder

        AccountingPeriodResponseDTO period = service.reopenPeriod(year, month, currentUser, request.getNotes());

        return ResponseEntity.ok(new ApiResponse<>(
                String.format("Period %d-%02d reopened", year, month),
                true,
                period
        ));
    }

    /**
     * Checks if a period is closed.
     */
    @GetMapping("/{year}/{month}/is-closed")
    @Operation(summary = "Check if period is closed", description = "Utility endpoint to verify if a specific period is locked for accounting entries.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status retrieved successfully")
    public ResponseEntity<ApiResponse<Boolean>> isPeriodClosed(
            @PathVariable Integer year,
            @PathVariable Integer month) {

        boolean isClosed = service.isPeriodClosed(year, month);

        return ResponseEntity.ok(new ApiResponse<>(
                String.format("Period %d-%02d status: %s", year, month, isClosed ? "CLOSED" : "OPEN"),
                true,
                isClosed
        ));
    }
}