package com.erp.erp_cloud.dto.reports.financial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Hierarchical Income Statement (Estado de Resultados / Pérdidas y
 * Ganancias) for a date range, with full PUC rollup at every level --
 * same tree-walking approach as TrialBalanceReportDetailed, but scoped
 * to Ingresos (4), Gastos (5) and Costos (6/7), and with no opening
 * balance (Income Statement accounts are temporary).
 *
 * Modeled after a report the user's legacy system (Siewin) produces,
 * shown to us on 2026-10-12: three hierarchical blocks (Ingresos,
 * Gastos, Costo de Ventas) each with a subtotal, and a bottom line
 * labeled "Utilidad antes de Impuestos" or "Pérdida antes de
 * Impuestos" depending on the sign of the result -- the user asked
 * explicitly for that dynamic label, so it's computed here rather than
 * left for the frontend to guess from a signed number.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomeStatementDetailedReport {

    /**
     * Company name for the report header.
     */
    private String companyName;

    /**
     * Start date of the reporting period.
     */
    private LocalDate startDate;

    /**
     * End date of the reporting period.
     */
    private LocalDate endDate;

    /**
     * When this report was generated.
     */
    private LocalDateTime generatedAt;

    // ═══════════════════════════════════════════════════════════
    // INGRESOS (class 4)
    // ═══════════════════════════════════════════════════════════

    private List<IncomeStatementLineDetailed> revenueLines;

    /**
     * Sum of all Ingresos leaf accounts (posting accounts only -- a
     * header's total is a pure rollup of these, counting it too would
     * double-count, same rule as TrialBalanceReportDetailed).
     */
    private BigDecimal totalRevenue;

    // ═══════════════════════════════════════════════════════════
    // GASTOS (class 5)
    // ═══════════════════════════════════════════════════════════

    private List<IncomeStatementLineDetailed> expenseLines;

    private BigDecimal totalExpenses;

    // ═══════════════════════════════════════════════════════════
    // COSTOS (class 6/7)
    // ═══════════════════════════════════════════════════════════

    private List<IncomeStatementLineDetailed> costLines;

    private BigDecimal totalCosts;

    // ═══════════════════════════════════════════════════════════
    // RESULTADO
    // ═══════════════════════════════════════════════════════════

    /**
     * Net result before taxes = totalRevenue − totalExpenses − totalCosts.
     * Positive = profit, negative = loss -- same natural-sign
     * convention as IncomeStatementReport.netIncome elsewhere in this
     * codebase.
     */
    private BigDecimal netResultBeforeTaxes;

    /**
     * "Profit Before Taxes" or "Loss Before Taxes", chosen from the
     * sign of netResultBeforeTaxes.
     */
    private String resultLabel;

    /**
     * "Utilidad antes de Impuestos" o "Pérdida antes de Impuestos",
     * elegido según el signo de netResultBeforeTaxes -- la etiqueta que
     * el usuario pidió explícitamente (2026-10-12).
     */
    private String resultLabelEs;

    // ═══════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════

    public boolean isProfitable() {
        return netResultBeforeTaxes != null && netResultBeforeTaxes.compareTo(BigDecimal.ZERO) >= 0;
    }

    /**
     * Returns formatted report title.
     */
    public String getReportTitle() {
        return String.format("Income Statement - %s to %s", startDate, endDate);
    }

    /**
     * Returns formatted report title in Spanish.
     */
    public String getReportTitleEs() {
        return String.format("Estado de Resultados - %s al %s", startDate, endDate);
    }

    /**
     * Returns the net result as a positive magnitude -- the sign is
     * already conveyed by resultLabel/resultLabelEs, so the frontend
     * doesn't need to re-derive it.
     */
    public BigDecimal getNetResultMagnitude() {
        return netResultBeforeTaxes != null ? netResultBeforeTaxes.abs() : BigDecimal.ZERO;
    }

    public String getFormattedTotalRevenue() {
        return String.format("$%,.2f", totalRevenue != null ? totalRevenue : BigDecimal.ZERO);
    }

    public String getFormattedTotalExpenses() {
        return String.format("$%,.2f", totalExpenses != null ? totalExpenses : BigDecimal.ZERO);
    }

    public String getFormattedTotalCosts() {
        return String.format("$%,.2f", totalCosts != null ? totalCosts : BigDecimal.ZERO);
    }

    public String getFormattedNetResultBeforeTaxes() {
        return String.format("$%,.2f", getNetResultMagnitude());
    }
}
