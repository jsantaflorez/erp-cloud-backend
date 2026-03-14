package com.erp.erp_cloud.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Represents the normal balance nature of an account.
 *
 * In double-entry accounting:
 * - Debit accounts: Assets, Expenses, Costs
 * - Credit accounts: Liabilities, Equity, Revenue
 */
public enum AccountNature {

    D("Debit", "Débito"),
    C("Credit", "Crédito");

    private final String displayName;
    private final String displayNameEs;

    AccountNature(String displayName, String displayNameEs) {
        this.displayName = displayName;
        this.displayNameEs = displayNameEs;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDisplayNameEs() {
        return displayNameEs;
    }

    /**
     * Returns the single-character value for JSON serialization.
     * This ensures JSON shows "D" or "C" instead of "DEBIT" or "CREDIT".
     */
    @JsonValue
    public String getValue() {
        return this.name();
    }

    /**
     * Determines the normal balance nature from account class.
     */
    public static AccountNature fromAccountClass(AccountClass accountClass) {
        return switch (accountClass) {
            case ASSET, EXPENSE, COST -> D;
            case LIABILITY, EQUITY, REVENUE -> C;
        };
    }
}