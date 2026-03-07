package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.dto.TrialBalanceReport;
import com.erp.erp_cloud.service.JournalEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final JournalEntryService journalEntryService;

    /**
     * Generates the Trial Balance (Balance de Prueba) for the current company.
     * This report sums up all debits and credits grouped by account.
     */
    @GetMapping("/trial-balance")
    public ResponseEntity<ApiResponse<TrialBalanceReport>> getTrialBalance() {
        TrialBalanceReport data = journalEntryService.getTrialBalanceReport();

        String message = data.isBalanced()
                ? "Trial balance generated successfully and is balanced."
                : "Trial balance generated, but an imbalance was detected!";

        return ResponseEntity.ok(new ApiResponse<>(message, true, data));
    }
}