package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.dto.reports.financial.*;
import com.erp.erp_cloud.enums.TaxCategory;
import com.erp.erp_cloud.service.reports.financial.AuxiliaryLedgerService;
import com.erp.erp_cloud.service.reports.financial.CostCenterReportService;
import com.erp.erp_cloud.service.reports.financial.ThirdPartyReportService;
import com.erp.erp_cloud.service.reports.financial.FinancialStatementService;
import com.erp.erp_cloud.service.reports.financial.TaxDetailReportService;
import com.erp.erp_cloud.service.JournalEntryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Financial Reports", description = "Endpoints for generating accounting books and financial statements (Balance Sheet, Trial Balance, etc.)")
public class ReportController {

    private final JournalEntryService journalEntryService;
    private final FinancialStatementService financialStatementService;
    private final AuxiliaryLedgerService auxiliaryLedgerService;
    private final CostCenterReportService costCenterReportService;
    private final ThirdPartyReportService thirdPartyReportService;
    private final TaxDetailReportService taxDetailReportService;

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
    @Operation(summary = "Generate Trial Balance", description = "Generates the 'Balance de Comprobación' showing current balances for all accounts as of a specific date.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Trial balance generated successfully")
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
    @Operation(summary = "Generate Balance Sheet", description = "Generates the 'Estado de Situación Financiera' (Assets, Liabilities, and Equity) as of a specific date.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Balance Sheet generated successfully")
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
    @Operation(summary = "Generate Detailed Trial Balance", description = "Shows opening balances, period movements (debits/credits), and closing balances for a date range.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Detailed report generated successfully")
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
    @Operation(summary = "Generate Auxiliary Ledger", description = "Retrieves the 'Libro Auxiliar' showing detailed transaction history and running balances for a range of accounts.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Auxiliary Ledger generated successfully")
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

    // ═══════════════════════════════════════════════════════════
    // COST CENTER BALANCE
    // ═══════════════════════════════════════════════════════════

    /**
     * Generates the "Balance por Centro de Costo" report: total debit and
     * credit movement per cost center within a date range.
     *
     * Example URL:
     * - GET /api/v1/reports/cost-center-balance?startDate=2026-01-01&endDate=2026-12-31
     */
    @GetMapping("/cost-center-balance")
    @Operation(summary = "Generate Cost Center Balance (Auxiliar por Centro de Costo)", description = "Shows opening balance, transactions and closing balance per cost center for a date range, optionally restricted to an account range and/or a single cost center.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cost center balance generated successfully")
    public ResponseEntity<ApiResponse<com.erp.erp_cloud.dto.reports.financial.CostCenterBalanceReport>> getCostCenterBalance(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @RequestParam(required = false, defaultValue = "1")
            String startCode,

            @RequestParam(required = false, defaultValue = "9999999999")
            String endCode,

            @RequestParam(required = false)
            String costCenterCode) {

        com.erp.erp_cloud.dto.reports.financial.CostCenterBalanceReport data =
                costCenterReportService.getCostCenterBalanceReport(startDate, endDate, startCode, endCode, costCenterCode);

        String message = String.format(
                "Cost center balance generated successfully from %s to %s. %d cost center(s).",
                startDate, endDate, data.getCostCenterGroups() != null ? data.getCostCenterGroups().size() : 0
        );

        return ResponseEntity.ok(new ApiResponse<>(message, true, data));
    }

    // ═══════════════════════════════════════════════════════════
    // THIRD PARTY BALANCE
    // ═══════════════════════════════════════════════════════════

    /**
     * Generates the "Estado de Cuenta por Tercero" report: opening
     * balance, transactions and closing balance per third party within a
     * date range.
     *
     * Example URL:
     * - GET /api/v1/reports/third-party-balance?startDate=2026-01-01&endDate=2026-12-31
     */
    @GetMapping("/third-party-balance")
    @Operation(summary = "Generate Third Party Balance (Estado de Cuenta por Tercero)", description = "Shows opening balance, transactions and closing balance per third party for a date range, optionally restricted to an account range, a single third party and/or a single cost center.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Third party balance generated successfully")
    public ResponseEntity<ApiResponse<com.erp.erp_cloud.dto.reports.financial.ThirdPartyBalanceReport>> getThirdPartyBalance(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @RequestParam(required = false, defaultValue = "1")
            String startCode,

            @RequestParam(required = false, defaultValue = "9999999999")
            String endCode,

            @RequestParam(required = false)
            String thirdPartyDocument,

            @RequestParam(required = false)
            String costCenterCode) {

        com.erp.erp_cloud.dto.reports.financial.ThirdPartyBalanceReport data =
                thirdPartyReportService.getThirdPartyBalanceReport(startDate, endDate, startCode, endCode, thirdPartyDocument, costCenterCode);

        String message = String.format(
                "Third party balance generated successfully from %s to %s. %d third part%s.",
                startDate, endDate, data.getThirdPartyGroups() != null ? data.getThirdPartyGroups().size() : 0,
                (data.getThirdPartyGroups() != null && data.getThirdPartyGroups().size() == 1) ? "y" : "ies"
        );

        return ResponseEntity.ok(new ApiResponse<>(message, true, data));
    }

    // ═══════════════════════════════════════════════════════════
    // INCOME STATEMENT (DETAILED / HIERARCHICAL)
    // ═══════════════════════════════════════════════════════════

    /**
     * Generates the hierarchical Income Statement (Estado de Resultados
     * / Pérdidas y Ganancias) for a date range -- full PUC rollup at
     * every level (Ingresos / Gastos / Costo de Ventas), with the
     * bottom line labeled "Utilidad antes de Impuestos" or "Pérdida
     * antes de Impuestos" depending on the result's sign.
     *
     * Distinct from the existing getIncomeStatement() flat analysis
     * (gross/operating margins, etc., never exposed over HTTP) -- this
     * is the report the user asked for 2026-10-12, modeled after their
     * legacy system's "PERDIDA O GANANCIA" layout.
     *
     * @param startDate Start of the reporting period (required)
     * @param endDate End of the reporting period (required)
     * @return Hierarchical Income Statement
     *
     * Example URL:
     * - GET /api/v1/reports/income-statement-detailed?startDate=2026-01-01&endDate=2026-12-31
     */
    @GetMapping("/income-statement-detailed")
    @Operation(summary = "Generate Detailed Income Statement (Estado de Resultados)", description = "Shows a full PUC-hierarchy Income Statement (Ingresos / Gastos / Costo de Ventas) for a date range, with Utilidad/Pérdida antes de Impuestos as the bottom line.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Detailed Income Statement generated successfully")
    public ResponseEntity<ApiResponse<IncomeStatementDetailedReport>> getIncomeStatementDetailed(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate) {

        IncomeStatementDetailedReport data = financialStatementService.getIncomeStatementDetailed(startDate, endDate);

        String message = String.format(
                "Estado de Resultados generado del %s al %s. %s.",
                startDate, endDate, data.getResultLabelEs());

        return ResponseEntity.ok(new ApiResponse<>(message, true, data));
    }

    // ═══════════════════════════════════════════════════════════
    // TAX DETAIL REPORT -- "Informe Detallado de Impuestos y
    // Retenciones" (2026-10-16, unifies what would otherwise be one
    // report per Tax category -- IVA, Retenciones, Impoconsumo and any
    // future custom tax all go through this single endpoint, with
    // category as an optional filter instead of a hardcoded one)
    // ═══════════════════════════════════════════════════════════

    /**
     * Generates the "Informe Detallado de Impuestos y Retenciones": a
     * flat, chronological list of every tax-linked journal entry line
     * within a date range, optionally narrowed to one Tax category
     * (RETENCION/IVA/OTRO -- omitted means all three), one specific tax
     * type (e.g. "RETEFUENTE", "IVA_GENERADO", "IMPOCONSUMO") and/or one
     * third party, with subtotals per type and grand totals -- used to
     * liquidate monthly/period tax declarations (IVA, retenciones, or
     * any other tax) and validate bases before issuing certificates.
     *
     * @param startDate          Start of the reporting period (required)
     * @param endDate            End of the reporting period (required)
     * @param category           Optional: RETENCION, IVA or OTRO -- omitted means all
     * @param taxType            Optional: one specific tax type (e.g. "RETEFUENTE")
     * @param thirdPartyDocument Optional: one specific third party by document number
     *
     * Example URL:
     * - GET /api/v1/reports/tax-detail?startDate=2026-01-01&endDate=2026-01-31&category=RETENCION
     */
    @GetMapping("/tax-detail")
    @Operation(summary = "Generate Tax Detail Report (Informe Detallado de Impuestos y Retenciones)", description = "Shows every tax-linked journal entry line (IVA, Retenciones, Impoconsumo or any other category) for a date range, optionally restricted to one category, one tax type and/or one third party, with subtotals per type.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tax detail report generated successfully")
    public ResponseEntity<ApiResponse<TaxDetailReport>> getTaxDetail(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @RequestParam(required = false)
            TaxCategory category,

            @RequestParam(required = false)
            String taxType,

            @RequestParam(required = false)
            String thirdPartyDocument) {

        TaxDetailReport data =
                taxDetailReportService.getTaxDetailReport(startDate, endDate, category, taxType, thirdPartyDocument);

        String message = String.format(
                "Tax detail report generated successfully from %s to %s. %d record(s).",
                startDate, endDate, data.getTotalRecords());

        return ResponseEntity.ok(new ApiResponse<>(message, true, data));
    }

}