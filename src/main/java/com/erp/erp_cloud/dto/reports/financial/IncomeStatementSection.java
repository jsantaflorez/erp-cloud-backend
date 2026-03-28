package com.erp.erp_cloud.dto.reports.financial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Represents a section/category within the Income Statement.
 *
 * Examples:
 * - Operating Revenue
 * - Cost of Goods Sold
 * - Administrative Expenses
 * - Tax Expenses
 *
 * Each section contains multiple account lines and a subtotal.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomeStatementSection implements FinancialSection {

    /**
     * Section name in English.
     * Example: "Operating Revenue", "Administrative Expenses"
     */
    private String sectionName;

    /**
     * Section name in Spanish.
     * Example: "Ingresos Operacionales", "Gastos Administrativos"
     */
    private String sectionNameEs;

    /**
     * Individual account lines within this section.
     * Each line represents one account with its balance.
     */
    private List<AccountLine> accountLines;

    /**
     * Total of all account balances in this section.
     * Sum of all accountLines balances.
     */
    private BigDecimal sectionTotal;

    /**
     * Display order for sorting sections.
     * Lower numbers appear first in the report.
     */
    private Integer displayOrder;

    // ═══════════════════════════════════════════════════════════
    // NESTED CLASS: Individual Account Line
    // ═══════════════════════════════════════════════════════════

    /**
     * Represents a single account line within a section.
     *
     * Example:
     * - Account: 410505 - Ingresos por Ventas
     * - Amount: $1,000,000.00
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountLine {

        /**
         * Account code (e.g., "410505")
         */
        private String accountCode;

        /**
         * Account name (e.g., "Ingresos por Ventas")
         */
        private String accountName;

        /**
         * Account balance for the period.
         *
         * For Revenue: Credit balance (shown as positive)
         * For Costs: Debit balance (shown as positive)
         * For Expenses: Debit balance (shown as positive)
         */
        private BigDecimal amount;

        /**
         * Returns formatted account description.
         * Example: "410505 - Ingresos por Ventas"
         */
        public String getFullDescription() {
            return accountCode + " - " + accountName;
        }

        /**
         * Returns formatted amount with currency.
         * Example: "$1,000,000.00"
         */
        public String getFormattedAmount() {
            if (amount == null) return "$0.00";
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                return String.format("($%,.2f)", amount.abs());
            }
            return String.format("$%,.2f", amount);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════

    /**
     * Returns the number of accounts in this section.
     */
    public int getAccountCount() {
        return accountLines != null ? accountLines.size() : 0;
    }

    /**
     * Returns formatted section total with currency.
     * Example: "$1,200,000.00"
     */
    public String getFormattedSectionTotal() {
        if (sectionTotal == null) return "$0.00";
        if (sectionTotal.compareTo(BigDecimal.ZERO) < 0) {
            return String.format("($%,.2f)", sectionTotal.abs());
        }
        return String.format("$%,.2f", sectionTotal);
    }

    /**
     * Checks if this section has any accounts.
     */
    public boolean hasAccounts() {
        return accountLines != null && !accountLines.isEmpty();
    }
}
//```
//
//        ---
//
//        ## **📋 DTO Structure Visualization**
//        ```
//IncomeStatementReport
//├── companyName: "ABC Company"
//        ├── startDate: 2026-01-01
//        ├── endDate: 2026-12-31
//        │
//        ├── revenueSections: [
//        │   ├── IncomeStatementSection {
//│   │   ├── sectionName: "Operating Revenue"
//│   │   ├── sectionTotal: $1,200,000
//│   │   └── accountLines: [
//│   │       ├── AccountLine { code: "410505", name: "Ventas", amount: $1,000,000 }
//│   │       └── AccountLine { code: "420505", name: "Servicios", amount: $200,000 }
//│   │       ]
//│   │   }
//│   └── IncomeStatementSection {
//│       ├── sectionName: "Non-Operating Revenue"
//│       ├── sectionTotal: $60,000
//│       └── accountLines: [...]
//│       }
//│   ]
//        │
//        ├── totalRevenue: $1,260,000
//        │
//        ├── costSections: [...]
//        ├── totalCosts: $500,000
//        ├── grossProfit: $760,000
//        │
//        ├── operatingExpenseSections: [...]
//        ├── totalOperatingExpenses: $470,000
//        ├── operatingIncome: $290,000
//        │
//        ├── taxExpenseSections: [...]
//        ├── totalTaxExpenses: $62,500
//        │
//        ├── netIncome: $187,500
//        │
//        ├── grossProfitMargin: 60.3
//        ├── operatingMargin: 23.0
//        └── netProfitMargin: 14.9