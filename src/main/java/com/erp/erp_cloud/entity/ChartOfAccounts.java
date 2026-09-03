package com.erp.erp_cloud.entity;



import com.erp.erp_cloud.entity.BaseEntity;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.enums.AccountCategory;
import com.erp.erp_cloud.enums.AccountClass;
import com.erp.erp_cloud.enums.AccountNature;
import com.erp.erp_cloud.enums.FinancialStatement;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "chart_of_accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_company_code", columnNames = {"company_id", "code"})
        },
        indexes = {
                // Existing indexes
                @Index(name = "idx_coa_company_code", columnList = "company_id, code"),
                @Index(name = "idx_coa_parent", columnList = "company_id, parent_id"),
                @Index(name = "idx_coa_posting", columnList = "company_id, posting_account, active"),
                @Index(name = "idx_coa_level", columnList = "company_id, level, active"),
                @Index(name = "idx_coa_name", columnList = "company_id, name"),
                @Index(name = "idx_coa_class", columnList = "company_id, account_class"),

                // NEW indexes for financial statements
                @Index(name = "idx_coa_category", columnList = "company_id, account_category"),
                @Index(name = "idx_coa_statement", columnList = "company_id, financial_statement"),
                @Index(name = "idx_coa_year_end", columnList = "company_id, closes_at_year_end")
        }
)
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ChartOfAccounts extends BaseEntity implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private Byte level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 1)
    private AccountNature nature;

    // ═══════════════════════════════════════════════════════════
    // FINANCIAL STATEMENT FIELDS
    // ═══════════════════════════════════════════════════════════

    /**
     * Main account classification (top-level grouping).
     * Examples: ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE, COST
     *
     * Changed from String to Enum for type safety.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_class", nullable = false, length = 20)
    private AccountClass accountClass;

    /**
     * Detailed category within the account class.
     * Examples: CURRENT_ASSET, FIXED_ASSET, CURRENT_LIABILITY, etc.
     *
     * This is CRITICAL for generating financial statements.
     * Allows automated grouping in Balance Sheet and Income Statement.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_category", nullable = false, length = 50)
    private AccountCategory accountCategory;

    /**
     * Which financial statement this account appears in.
     * - BALANCE_SHEET: Assets, Liabilities, Equity
     * - INCOME_STATEMENT: Revenue, Expenses, Costs
     * - CASH_FLOW: Cash and cash equivalents
     * - EQUITY_CHANGES: Changes in equity
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "financial_statement", nullable = false, length = 30)
    private FinancialStatement financialStatement;

    /**
     * Determines if this account closes at year-end.
     *
     * TRUE  = Revenue/Expense/Cost accounts (temporary accounts)
     *         - Close to Retained Earnings at year-end
     *         - Start with zero balance in new year
     *
     * FALSE = Asset/Liability/Equity accounts (permanent accounts)
     *         - Balances carry forward to new year
     *         - Never close
     */
    @Column(name = "closes_at_year_end", nullable = false)
    private boolean closesAtYearEnd = false;

    /**
     * Display order in financial statements.
     * Lower numbers appear first.
     *
     * Examples:
     * - Current Assets: 10
     * - Fixed Assets: 20
     * - Current Liabilities: 30
     * - Revenue: 40
     * - Expenses: 50
     */
    @Column(name = "display_order")
    private Integer displayOrder;

    // ═══════════════════════════════════════════════════════════
    // EXISTING FIELDS (Unchanged)
    // ═══════════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════════
    // BUSINESS RULES
    // ═══════════════════════════════════════════════════════════

    @Column(name = "posting_account", nullable = false)
    private boolean postingAccount = false;

    @Column(name = "requires_third_party", nullable = false)
    private boolean requiresThirdParty = false;

    @Column(name = "requires_cost_center", nullable = false)
    private boolean requiresCostCenter = false;

    @Column(nullable = false)
    private boolean active = true;

    // ═══════════════════════════════════════════════════════════
    // HIERARCHY
    // ═══════════════════════════════════════════════════════════


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @JsonIgnoreProperties({"parent", "children", "company"})
    private ChartOfAccounts parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<ChartOfAccounts> children = new ArrayList<>();

    // ═══════════════════════════════════════════════════════════
    // BUSINESS METHODS
    // ═══════════════════════════════════════════════════════════

    /**
     * Returns a human-readable description combining code and name.
     * Example: "110505 - Caja General"
     */
    public String getFullDescription() {
        return code + " - " + name;
    }

    /**
     * Checks if this account is a balance sheet account.
     * Balance sheet accounts carry balances forward to new fiscal years.
     */
    public boolean isBalanceSheetAccount() {
        return financialStatement == FinancialStatement.BALANCE_SHEET;
    }

    /**
     * Checks if this account is an income statement account.
     * Income statement accounts close at year-end.
     */
    public boolean isIncomeStatementAccount() {
        return financialStatement == FinancialStatement.INCOME_STATEMENT;
    }

    /**
     * Returns the display name for the account category.
     * Useful for UI dropdowns and reports.
     */
    public String getCategoryDisplayName() {
        return accountCategory != null ? accountCategory.getDisplayName() : "";
    }

    /**
     * Returns the display name for the financial statement.
     */
    public String getFinancialStatementDisplayName() {
        return financialStatement != null ? financialStatement.getDisplayName() : "";
    }

    // ═══════════════════════════════════════════════════════════
    // VALIDATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Validates entity before persisting or updating.
     * Ensures business rules are followed.
     */
    @PrePersist
    @PreUpdate
    void validateEntity() {
        // Validate code and name
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Account code cannot be empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Account name cannot be empty");
        }

        // Validate level
        if (level == null || level < 1) {
            throw new IllegalArgumentException("Account level must be at least 1");
        }

        // Validate category matches class
        validateCategoryMatchesClass();

        // Validate financial statement matches class
        validateFinancialStatementMatchesClass();

        // Auto-set closesAtYearEnd based on financial statement
        if (financialStatement == FinancialStatement.INCOME_STATEMENT) {
            this.closesAtYearEnd = true;
        } else if (financialStatement == FinancialStatement.BALANCE_SHEET) {
            this.closesAtYearEnd = false;
        }
    }

    /**
     * Validates that the account category matches the account class.
     */
    private void validateCategoryMatchesClass() {
        if (accountClass == null || accountCategory == null) {
            return; // Will be caught by nullable constraint
        }

        boolean isValid = switch (accountClass) {
            case ASSET -> accountCategory.name().contains("ASSET") ||
                    accountCategory == AccountCategory.INVESTMENT;
            case LIABILITY -> accountCategory.name().contains("LIABILITY") ||
                    accountCategory == AccountCategory.TAXES_PAYABLE;
            case EQUITY -> accountCategory.name().contains("EQUITY") ||
                    accountCategory == AccountCategory.RETAINED_EARNINGS ||
                    accountCategory == AccountCategory.SHARE_CAPITAL ||
                    accountCategory == AccountCategory.CURRENT_YEAR_PROFIT ||
                    accountCategory == AccountCategory.RESERVES;
            case REVENUE -> accountCategory.name().contains("REVENUE");
            case EXPENSE -> accountCategory.name().contains("EXPENSE") ||
                    accountCategory == AccountCategory.TAX_EXPENSE;
            case COST -> accountCategory == AccountCategory.COST_OF_SALES;
        };

        if (!isValid) {
            throw new InvalidOperationException(
                    String.format("Account category %s does not match account class %s",
                            accountCategory, accountClass),
                    "CATEGORY_CLASS_MISMATCH"
            );
        }
    }

    /**
     * Validates that the financial statement matches the account class.
     */
    private void validateFinancialStatementMatchesClass() {
        if (accountClass == null || financialStatement == null) {
            return;
        }

        boolean isValid = switch (accountClass) {
            case ASSET, LIABILITY, EQUITY ->
                    financialStatement == FinancialStatement.BALANCE_SHEET;
            case REVENUE, EXPENSE, COST ->
                    financialStatement == FinancialStatement.INCOME_STATEMENT;
        };

        if (!isValid) {
            throw new InvalidOperationException(
                    String.format("Financial statement %s does not match account class %s",
                            financialStatement, accountClass),
                    "FINANCIAL_STATEMENT_CLASS_MISMATCH"
            );
        }
    }
}