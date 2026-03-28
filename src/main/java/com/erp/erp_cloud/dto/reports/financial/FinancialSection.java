package com.erp.erp_cloud.dto.reports.financial;

import java.math.BigDecimal;

/**
 * Common interface for all financial statement sections.
 *
 * Allows polymorphic processing of different section types
 * (Balance Sheet, Income Statement, Cash Flow, etc.) without
 * knowing their concrete implementation.
 *
 * Implemented by:
 * - BalanceSheetSection
 * - IncomeStatementSection
 * - CashFlowSection (future)
 * - etc.
 */
public interface FinancialSection {

    /**
     * Returns the total amount for this section.
     * This is the sum of all account lines within the section.
     *
     * @return Section total as BigDecimal
     */
    BigDecimal getSectionTotal();

    /**
     * Returns the display order for sorting sections in reports.
     * Lower numbers appear first.
     *
     * @return Display order as Integer
     */
    Integer getDisplayOrder();

    /**
     * Returns the section name in English.
     *
     * @return Section name (e.g., "Current Assets", "Operating Revenue")
     */
    String getSectionName();

    /**
     * Returns the section name in Spanish.
     *
     * @return Section name in Spanish (e.g., "Activos Corrientes", "Ingresos Operacionales")
     */
    String getSectionNameEs();
}