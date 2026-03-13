package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.AccountingPeriodRequest;
import com.erp.erp_cloud.dto.AccountingPeriodResponseDTO;
import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.service.AccountingPeriodService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounting-periods")
@RequiredArgsConstructor
public class AccountingPeriodController {

    private final AccountingPeriodService service;

    /**
     * Gets all accounting periods for the current company.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountingPeriodResponseDTO>>> getAllPeriods() {
        List<AccountingPeriodResponseDTO> periods = service.getAllPeriods();
        return ResponseEntity.ok(new ApiResponse<>("Periods retrieved successfully", true, periods));
    }

    /**
     * Gets all closed periods for the current company.
     */
    @GetMapping("/closed")
    public ResponseEntity<ApiResponse<List<AccountingPeriodResponseDTO>>> getClosedPeriods() {
        List<AccountingPeriodResponseDTO> periods = service.getClosedPeriods();
        return ResponseEntity.ok(new ApiResponse<>("Closed periods retrieved", true, periods));
    }

    /**
     * Gets all open periods for the current company.
     */
    @GetMapping("/open")
    public ResponseEntity<ApiResponse<List<AccountingPeriodResponseDTO>>> getOpenPeriods() {
        List<AccountingPeriodResponseDTO> periods = service.getOpenPeriods();
        return ResponseEntity.ok(new ApiResponse<>("Open periods retrieved", true, periods));
    }

    /**
     * Gets a specific accounting period.
     */
    @GetMapping("/{year}/{month}")
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