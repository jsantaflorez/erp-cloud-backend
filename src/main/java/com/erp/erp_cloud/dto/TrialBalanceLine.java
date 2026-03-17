package com.erp.erp_cloud.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Represents a single line in the Trial Balance report.
 *
 * Each line shows an account's activity:
 * - Total debits posted to the account
 * - Total credits posted to the account
 * - Net balance (difference between debits and credits)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrialBalanceLine {

    /**
     * Account code (e.g., "110505")
     */
    private String accountCode;

    /**
     * Account name (e.g., "Caja General")
     */
    private String accountName;

    /**
     * Sum of all debit entries for this account.
     */
    private BigDecimal totalDebit;

    /**
     * Sum of all credit entries for this account.
     */
    private BigDecimal totalCredit;

    /**
     * Net balance calculated based on account nature:
     * - Debit accounts (Assets, Expenses): Debit - Credit
     * - Credit accounts (Liabilities, Equity, Revenue): Credit - Debit
     *
     * This is calculated automatically in the repository query.
     */
    private BigDecimal netBalance;

    /**
     * Constructor for JPA query projection.
     * Used in repository queries that calculate balances.
     */
    public TrialBalanceLine(String accountCode, String accountName,
                            BigDecimal totalDebit, BigDecimal totalCredit) {
        this.accountCode = accountCode;
        this.accountName = accountName;
        this.totalDebit = totalDebit;
        this.totalCredit = totalCredit;
        this.netBalance = totalDebit.subtract(totalCredit);
    }

    // ═══════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════

    /**
     * Returns formatted account description.
     * Example: "110505 - Caja General"
     */
    public String getFullDescription() {
        return accountCode + " - " + accountName;
    }

    /**
     * Returns formatted total debit with currency.
     * Example: "$50,000.00"
     */
    public String getFormattedTotalDebit() {
        return String.format("$%,.2f", totalDebit != null ? totalDebit : BigDecimal.ZERO);
    }

    /**
     * Returns formatted total credit with currency.
     * Example: "$50,000.00"
     */
    public String getFormattedTotalCredit() {
        return String.format("$%,.2f", totalCredit != null ? totalCredit : BigDecimal.ZERO);
    }

    /**
     * Returns formatted net balance with currency.
     * Example: "$50,000.00" or "($50,000.00)" for negatives
     */
    public String getFormattedNetBalance() {
        if (netBalance == null) {
            return "$0.00";
        }
        if (netBalance.compareTo(BigDecimal.ZERO) < 0) {
            return String.format("($%,.2f)", netBalance.abs());
        }
        return String.format("$%,.2f", netBalance);
    }

    /**
     * Checks if this account has any activity.
     */
    public boolean hasActivity() {
        return (totalDebit != null && totalDebit.compareTo(BigDecimal.ZERO) != 0) ||
                (totalCredit != null && totalCredit.compareTo(BigDecimal.ZERO) != 0);
    }
}