package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.dto.reports.financial.*;
import com.erp.erp_cloud.service.reports.financial.AuxiliaryLedgerService;
import com.erp.erp_cloud.service.reports.financial.FinancialStatementService;
import com.erp.erp_cloud.service.JournalEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final JournalEntryService journalEntryService;
    private final FinancialStatementService financialStatementService;
    private final AuxiliaryLedgerService auxiliaryLedgerService;

    // ═══════════════════════════════════════════════════════════
    // TRIAL BALANCE
    // ═══════════════════════════════════════════════════════════

    /**
     * Generates the Trial Balance (Balance de Comprobación) for the current company.
     *
     * @param asOfDate Optional date parameter (defaults to current date if not provided)
     * @return Trial Balance report with all account balances
     *
     * Example URLs:
     * - GET /api/reports/trial-balance (uses current date)
     * - GET /api/reports/trial-balance?asOfDate=2026-12-31
     */
    @GetMapping("/trial-balance")
    public ResponseEntity<ApiResponse<TrialBalanceReport>> getTrialBalance(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate asOfDate) {

        // If no date provided, use current date
        if (asOfDate == null) {
            asOfDate = LocalDate.now();
        }

        TrialBalanceReport data = journalEntryService.getTrialBalanceReport(asOfDate);

        String message = data.isBalanced()
                ? String.format("Trial balance generated successfully for %s and is balanced.", asOfDate)
                : String.format("Trial balance generated for %s, but an IMBALANCE was detected!", asOfDate);

        return ResponseEntity.ok(new ApiResponse<>(message, true, data));
    }

    // ═══════════════════════════════════════════════════════════
    // BALANCE SHEET
    // ═══════════════════════════════════════════════════════════

    /**
     * Generates the Balance Sheet (Estado de Situación Financiera) for the current company.
     *
     * @param asOfDate The date for which to generate the balance sheet
     * @return Complete balance sheet with assets, liabilities, and equity
     *
     * Example URL:
     * - GET /api/reports/balance-sheet?asOfDate=2026-12-31
     */
    @GetMapping("/balance-sheet")
    public ResponseEntity<ApiResponse<BalanceSheetReport>> getBalanceSheet(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate asOfDate) {

        BalanceSheetReport data = financialStatementService.getBalanceSheet(asOfDate);

        String message = data.isBalanced()
                ? String.format("Balance Sheet generated successfully for %s.", asOfDate)
                : String.format("WARNING: Balance Sheet for %s is OUT OF BALANCE!", asOfDate);

        return ResponseEntity.ok(new ApiResponse<>(message, true, data));
    }


    /**
     * Generates detailed Trial Balance with opening balances for a date range.
     *
     * Shows the complete movement of accounts:
     * - Opening balance (start of period)
     * - Period activity (debits/credits during period)
     * - Closing balance (end of period)
     *
     * @param startDate Start of the reporting period (required)
     * @param endDate End of the reporting period (required)
     * @return Detailed trial balance with opening balances
     *
     * Example URL:
     * - GET /api/reports/trial-balance-detailed?startDate=2026-01-01&endDate=2026-12-31
     */
    @GetMapping("/trial-balance-detailed")
    public ResponseEntity<ApiResponse<TrialBalanceReportDetailed>> getTrialBalanceDetailed(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate) {

        TrialBalanceReportDetailed data = journalEntryService.getTrialBalanceDetailed(startDate, endDate);

        String message = data.isBalanced()
                ? String.format("Detailed Trial Balance generated successfully for %s to %s and is balanced.",
                startDate, endDate)
                : String.format("Detailed Trial Balance generated for %s to %s, but an IMBALANCE was detected!",
                startDate, endDate);

        return ResponseEntity.ok(new ApiResponse<>(message, true, data));
   }

    /**
     * Generates Auxiliary Ledger Report (Libro Auxiliar por Cuenta).
     *
     * Shows detailed transaction history for a range of accounts with:
     * - Opening balance
     * - Transaction details
     * - Running balance
     *
     * @param startDate Start of period (required)
     * @param endDate End of period (required)
     * @param startCode First account code (optional, default: "1")
     * @param endCode Last account code (optional, default: "9999999999")
     * @return Auxiliary ledger report
     *
     * Examples:
     * - Single account: startCode="110505", endCode="110505"
     * - All cash accounts: startCode="11", endCode="119999"
     * - All accounts: startCode="1", endCode="9999999999"
     */
    @GetMapping("/auxiliary-ledger")
    public ResponseEntity<ApiResponse<AuxiliaryLedgerReport>> getAuxiliaryLedger(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @RequestParam(required = false, defaultValue = "1")
            String startCode,

            @RequestParam(required = false, defaultValue = "9999999999")
            String endCode) {

        AuxiliaryLedgerReport data = auxiliaryLedgerService.getAuxiliaryLedgerReport(
                startDate, endDate, startCode, endCode
        );

        int accountCount = data.getAccountGroups() != null ? data.getAccountGroups().size() : 0;
        int transactionCount = data.getAccountGroups() != null
                ? data.getAccountGroups().stream()
                .mapToInt(AuxiliaryAccountGroup::getTotalRecords)
                .sum()
                : 0;

        String message = String.format(
                "Auxiliary Ledger generated successfully from %s to %s. " +
                        "%d account(s), %d transaction(s).",
                startDate, endDate, accountCount, transactionCount
        );

        return ResponseEntity.ok(new ApiResponse<>(message, true, data));
    }

}