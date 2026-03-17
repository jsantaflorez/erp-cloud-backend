package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.dto.reports.financial.BalanceSheetReport;
import com.erp.erp_cloud.dto.reports.financial.TrialBalanceReport;
import com.erp.erp_cloud.dto.reports.financial.TrialBalanceReportDetailed;
import com.erp.erp_cloud.service.FinancialStatementService;
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
 //       Example JSON Response
//        {
//            "message": "Detailed Trial Balance generated successfully for 2026-01-01 to 2026-12-31 and is balanced.",
//                "success": true,
//                "data": {
//            "companyName": "ABC Company",
//                    "startDate": "2026-01-01",
//                    "endDate": "2026-12-31",
//                    "generatedAt": "2026-03-16",
//                    "lines": [
//            {
//                "accountCode": "110505",
//                    "accountName": "Caja General",
//                    "accountClass": "1 - Assets",
//                    "isBalanceSheetAccount": true,
//                    "openingBalance": 100000.00,
//                    "periodDebit": 500000.00,
//                    "periodCredit": 300000.00,
//                    "netMovement": 200000.00,
//                    "closingBalance": 300000.00
//            },
//            {
//                "accountCode": "410505",
//                    "accountName": "Ingresos por Ventas",
//                    "accountClass": "4 - Revenue",
//                    "isBalanceSheetAccount": false,
//                    "openingBalance": 0.00,
//                    "periodDebit": 0.00,
//                    "periodCredit": 1000000.00,
//                    "netMovement": -1000000.00,
//                    "closingBalance": -1000000.00
//            },
//            {
//                "accountCode": "510506",
//                    "accountName": "Gastos Administrativos",
//                    "accountClass": "5 - Expenses",
//                    "isBalanceSheetAccount": false,
//                    "openingBalance": 0.00,
//                    "periodDebit": 300000.00,
//                    "periodCredit": 0.00,
//                    "netMovement": 300000.00,
//                    "closingBalance": 300000.00
//            }
//    ],
//            "totalOpeningBalance": 100000.00,
//                    "totalPeriodDebit": 2000000.00,
//                    "totalPeriodCredit": 2000000.00,
//                    "totalNetMovement": 0.00,
//                    "totalClosingBalance": -400000.00,
//                    "isBalanced": true,
//                    "summaryByClass": {
//                "1 - Assets": 300000.00,
//                        "4 - Revenue": -1000000.00,
//                        "5 - Expenses": 300000.00
//            }
//        }
//        }
//```
//
//        ---
//
//## **✅ What You Now Have**
//
//### **Report 1: Simple Trial Balance** (existing)
//```
//        GET /api/reports/trial-balance?asOfDate=2026-12-31
//```
//        - Shows cumulative totals up to a date
//        - Quick balance verification
//        - Good for "snapshot" view
//
//### **Report 2: Detailed Trial Balance** (new)
//```
//        GET /api/reports/trial-balance-detailed?startDate=2026-01-01&endDate=2026-12-31
//
   }
}