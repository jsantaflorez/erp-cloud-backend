package com.erp.erp_cloud.dto.reports.financial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Income Statement (hierarchical) line, one per account in the active
 * chart of accounts for classes 4 (Ingresos), 5 (Gastos) and 6/7
 * (Costos) -- mirrors TrialBalanceLineDetailed's level/postingAccount
 * design so the frontend can reuse the same indentation/bold-header
 * rendering pattern already proven on the Balance de Comprobación
 * Detallado screen.
 *
 * Unlike the Trial Balance, there is no opening/closing balance here --
 * Income Statement accounts are temporary (closesAtYearEnd), so only
 * the period's own movement matters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomeStatementLineDetailed {

    /**
     * Account code (e.g., "413505"), or the group/class code for a
     * header row (e.g., "41", "4135").
     */
    private String accountCode;

    /**
     * Account or group name (e.g., "Ventas", "Comercio al por Mayor").
     */
    private String accountName;

    /**
     * Hierarchy depth (1 = class-level account like "4", 2 = group like
     * "41", etc. -- same numbering as ChartOfAccounts.level).
     */
    private Integer level;

    /**
     * True for a leaf/posting account (its own direct period
     * movement), false for a header/grouping account (its amount below
     * is a pure rollup of its descendants). Lets the frontend render
     * header rows distinctly (bold, as a subtotal) from detail rows --
     * same convention as TrialBalanceLineDetailed.postingAccount.
     */
    private boolean postingAccount;

    /**
     * Period movement, oriented to read naturally positive within its
     * own section: for Ingresos (class 4, credit-normal) this is
     * credit − debit; for Gastos/Costos (class 5/6/7, debit-normal)
     * this is debit − credit. A contra account (e.g. "Devoluciones en
     * Ventas", nature D inside Ingresos) nets correctly against its
     * siblings because the whole subtree is oriented once by its
     * top-level class, never by each account's own nature -- the same
     * fix already applied to the Balance Sheet and the flat Income
     * Statement (see FinancialStatementService for the full story on
     * why per-account orientation is wrong).
     */
    private BigDecimal amount;

    /**
     * Returns formatted account description.
     * Example: "413505 - Ventas"
     */
    public String getFullDescription() {
        return accountCode + " - " + accountName;
    }

    /**
     * Returns formatted amount with currency, parentheses for negative
     * (a negative amount here means a contra-subtree outweighs its own
     * section -- rare, but possible).
     */
    public String getFormattedAmount() {
        if (amount == null) return "$0.00";
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return String.format("($%,.2f)", amount.abs());
        }
        return String.format("$%,.2f", amount);
    }
}
