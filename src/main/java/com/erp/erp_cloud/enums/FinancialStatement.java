package com.erp.erp_cloud.enums;

/**
 * Represents the main financial statements in accounting.
 *
 * Determines which financial report an account appears in.
 */
public enum FinancialStatement {

    BALANCE_SHEET(
            "Balance Sheet",
            "Estado de Situación Financiera",
            "Statement of financial position showing assets, liabilities, and equity at a specific date"
    ),

    INCOME_STATEMENT(
            "Income Statement",
            "Estado de Resultados",
            "Statement showing revenue, expenses, and profit/loss for a period"
    ),

    CASH_FLOW(
            "Cash Flow Statement",
            "Estado de Flujos de Efectivo",
            "Statement showing cash inflows and outflows for a period"
    ),

    EQUITY_CHANGES(
            "Statement of Changes in Equity",
            "Estado de Cambios en el Patrimonio",
            "Statement showing changes in equity accounts during a period"
    );

    private final String displayName;
    private final String displayNameEs;
    private final String description;

    FinancialStatement(String displayName, String displayNameEs, String description) {
        this.displayName = displayName;
        this.displayNameEs = displayNameEs;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDisplayNameEs() {
        return displayNameEs;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Determines which financial statement an account class belongs to.
     */
    public static FinancialStatement fromAccountClass(AccountClass accountClass) {
        return switch (accountClass) {
            case ASSET, LIABILITY, EQUITY -> BALANCE_SHEET;
            case REVENUE, EXPENSE, COST -> INCOME_STATEMENT;
        };
    }

    /**
     * Checks if accounts in this statement close at year-end.
     */
    public boolean closesAtYearEnd() {
        return this == INCOME_STATEMENT;
    }
}