package com.erp.erp_cloud.dto.reports.financial;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Represents a section/category within the Balance Sheet.
 *
 * Examples:
 * - Current Assets
 * - Fixed Assets
 * - Current Liabilities
 * - Equity
 *
 * Each section contains multiple account lines and a subtotal.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceSheetSection {

    /**
     * Section name in English.
     * Example: "Current Assets", "Fixed Assets"
     */
    private String sectionName;

    /**
     * Section name in Spanish.
     * Example: "Activos Corrientes", "Activos Fijos"
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
     * - Account: 110505 - Caja General
     * - Balance: $50,000.00
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountLine {

        /**
         * Account code (e.g., "110505")
         */
        private String accountCode;

        /**
         * Account name (e.g., "Caja General")
         */
        private String accountName;

        /**
         * Account balance as of the report date.
         * Always positive (absolute value).
         */
        private BigDecimal balance;

        /**
         * Returns formatted account description.
         * Example: "110505 - Caja General"
         */
        public String getFullDescription() {
            return accountCode + " - " + accountName;
        }

        /**
         * Returns formatted balance with currency.
         * Example: "$50,000.00"
         */
        public String getFormattedBalance() {
            return String.format("$%,.2f", balance);
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
     * Example: "$250,000.00"
     */
    public String getFormattedSectionTotal() {
        return String.format("$%,.2f", sectionTotal != null ? sectionTotal : BigDecimal.ZERO);
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
//        ## **🎯 DTO Structure Visualization**
//        ```
//BalanceSheetReport
//├── companyName: "ABC Company"
//        ├── asOfDate: 2026-12-31
//        ├── isBalanced: true
//        │
//        ├── assetSections: [
//        │   ├── BalanceSheetSection
//│   │   ├── sectionName: "Current Assets"
//        │   │   ├── sectionTotal: $250,000.00
//        │   │   └── accountLines: [
//        │   │       ├── AccountLine { code: "110505", name: "Caja General", balance: $50,000 }
//│   │       ├── AccountLine { code: "111005", name: "Bancos", balance: $100,000 }
//│   │       └── AccountLine { code: "130505", name: "Clientes", balance: $100,000 }
//│   │       ]
//        │   │
//        │   └── BalanceSheetSection
//│       ├── sectionName: "Fixed Assets"
//        │       ├── sectionTotal: $400,000.00
//        │       └── accountLines: [...]
//        │   ]
//        │
//        ├── totalAssets: $650,000.00
//        │
//        ├── liabilitySections: [...]
//        ├── totalLiabilities: $275,000.00
//        │
//        ├── equitySections: [...]
//        ├── totalEquity: $375,000.00
//        │
//        └── totalLiabilitiesAndEquity: $650,000.00