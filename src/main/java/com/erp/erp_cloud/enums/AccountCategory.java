package com.erp.erp_cloud.enums;

/**
 * Detailed categorization of accounts for financial statement grouping.
 *
 * This provides the granular classification needed for:
 * - Balance Sheet sections
 * - Income Statement sections
 * - Automated financial reporting
 * - Year-end closing procedures
 */
public enum AccountCategory {

    // ═══════════════════════════════════════════════════════════
    // ASSETS (Activos)
    // ═══════════════════════════════════════════════════════════

    CURRENT_ASSET(
            "Current Assets",
            "Activos Corrientes",
            AccountClass.ASSET,
            10
    ),

    CASH_AND_EQUIVALENTS(
            "Cash and Cash Equivalents",
            "Efectivo y Equivalentes",
            AccountClass.ASSET,
            11
    ),

    ACCOUNTS_RECEIVABLE(
            "Accounts Receivable",
            "Cuentas por Cobrar",
            AccountClass.ASSET,
            12
    ),

    INVENTORY(
            "Inventory",
            "Inventarios",
            AccountClass.ASSET,
            13
    ),

    PREPAID_EXPENSES(
            "Prepaid Expenses",
            "Gastos Pagados por Anticipado",
            AccountClass.ASSET,
            14
    ),

    FIXED_ASSET(
            "Fixed Assets",
            "Activos Fijos",
            AccountClass.ASSET,
            20
    ),

    PROPERTY_PLANT_EQUIPMENT(
            "Property, Plant and Equipment",
            "Propiedad, Planta y Equipo",
            AccountClass.ASSET,
            21
    ),

    ACCUMULATED_DEPRECIATION(
            "Accumulated Depreciation",
            "Depreciación Acumulada",
            AccountClass.ASSET,
            22
    ),

    INTANGIBLE_ASSET(
            "Intangible Assets",
            "Activos Intangibles",
            AccountClass.ASSET,
            30
    ),

    INVESTMENT(
            "Investments",
            "Inversiones",
            AccountClass.ASSET,
            40
    ),

    OTHER_ASSET(
            "Other Assets",
            "Otros Activos",
            AccountClass.ASSET,
            90
    ),

    // ═══════════════════════════════════════════════════════════
    // LIABILITIES (Pasivos)
    // ═══════════════════════════════════════════════════════════

    CURRENT_LIABILITY(
            "Current Liabilities",
            "Pasivos Corrientes",
            AccountClass.LIABILITY,
            100
    ),

    ACCOUNTS_PAYABLE(
            "Accounts Payable",
            "Cuentas por Pagar",
            AccountClass.LIABILITY,
            101
    ),

    TAXES_PAYABLE(
            "Taxes Payable",
            "Impuestos por Pagar",
            AccountClass.LIABILITY,
            102
    ),

    ACCRUED_EXPENSES(
            "Accrued Expenses",
            "Gastos Acumulados",
            AccountClass.LIABILITY,
            103
    ),

    SHORT_TERM_DEBT(
            "Short-term Debt",
            "Deuda a Corto Plazo",
            AccountClass.LIABILITY,
            104
    ),

    LONG_TERM_LIABILITY(
            "Long-term Liabilities",
            "Pasivos a Largo Plazo",
            AccountClass.LIABILITY,
            110
    ),

    LONG_TERM_DEBT(
            "Long-term Debt",
            "Deuda a Largo Plazo",
            AccountClass.LIABILITY,
            111
    ),

    OTHER_LIABILITY(
            "Other Liabilities",
            "Otros Pasivos",
            AccountClass.LIABILITY,
            190
    ),

    // ═══════════════════════════════════════════════════════════
    // EQUITY (Patrimonio)
    // ═══════════════════════════════════════════════════════════

    SHARE_CAPITAL(
            "Share Capital",
            "Capital Social",
            AccountClass.EQUITY,
            200
    ),

    ADDITIONAL_PAID_IN_CAPITAL(
            "Additional Paid-in Capital",
            "Prima en Colocación de Acciones",
            AccountClass.EQUITY,
            201
    ),

    RETAINED_EARNINGS(
            "Retained Earnings",
            "Utilidades Retenidas",
            AccountClass.EQUITY,
            210
    ),

    CURRENT_YEAR_PROFIT(
            "Current Year Profit/Loss",
            "Utilidad/Pérdida del Ejercicio",
            AccountClass.EQUITY,
            220
    ),

    RESERVES(
            "Reserves",
            "Reservas",
            AccountClass.EQUITY,
            230
    ),

    TREASURY_STOCK(
            "Treasury Stock",
            "Acciones Propias en Cartera",
            AccountClass.EQUITY,
            240
    ),

    OTHER_EQUITY(
            "Other Equity",
            "Otro Patrimonio",
            AccountClass.EQUITY,
            290
    ),

    // ═══════════════════════════════════════════════════════════
    // REVENUE (Ingresos)
    // ═══════════════════════════════════════════════════════════

    OPERATING_REVENUE(
            "Operating Revenue",
            "Ingresos Operacionales",
            AccountClass.REVENUE,
            300
    ),

    SALES_REVENUE(
            "Sales Revenue",
            "Ingresos por Ventas",
            AccountClass.REVENUE,
            301
    ),

    SERVICE_REVENUE(
            "Service Revenue",
            "Ingresos por Servicios",
            AccountClass.REVENUE,
            302
    ),

    NON_OPERATING_REVENUE(
            "Non-operating Revenue",
            "Ingresos No Operacionales",
            AccountClass.REVENUE,
            310
    ),

    FINANCIAL_INCOME(
            "Financial Income",
            "Ingresos Financieros",
            AccountClass.REVENUE,
            311
    ),

    OTHER_REVENUE(
            "Other Revenue",
            "Otros Ingresos",
            AccountClass.REVENUE,
            390
    ),

    // ═══════════════════════════════════════════════════════════
    // EXPENSES (Gastos)
    // ═══════════════════════════════════════════════════════════

    OPERATING_EXPENSE(
            "Operating Expenses",
            "Gastos Operacionales",
            AccountClass.EXPENSE,
            400
    ),

    ADMINISTRATIVE_EXPENSE(
            "Administrative Expenses",
            "Gastos Administrativos",
            AccountClass.EXPENSE,
            401
    ),

    SALES_EXPENSE(
            "Sales and Marketing Expenses",
            "Gastos de Ventas",
            AccountClass.EXPENSE,
            402
    ),

    PERSONNEL_EXPENSE(
            "Personnel Expenses",
            "Gastos de Personal",
            AccountClass.EXPENSE,
            403
    ),

    DEPRECIATION_EXPENSE(
            "Depreciation and Amortization",
            "Depreciación y Amortización",
            AccountClass.EXPENSE,
            404
    ),

    FINANCIAL_EXPENSE(
            "Financial Expenses",
            "Gastos Financieros",
            AccountClass.EXPENSE,
            410
    ),

    NON_OPERATING_EXPENSE(
            "Non-operating Expenses",
            "Gastos No Operacionales",
            AccountClass.EXPENSE,
            420
    ),

    TAX_EXPENSE(
            "Tax Expenses",
            "Gastos por Impuestos",
            AccountClass.EXPENSE,
            430
    ),

    OTHER_EXPENSE(
            "Other Expenses",
            "Otros Gastos",
            AccountClass.EXPENSE,
            490
    ),

    // ═══════════════════════════════════════════════════════════
    // COSTS (Costos)
    // ═══════════════════════════════════════════════════════════

    COST_OF_SALES(
            "Cost of Sales",
            "Costo de Ventas",
            AccountClass.COST,
            500
    ),

    COST_OF_GOODS_SOLD(
            "Cost of Goods Sold",
            "Costo de Productos Vendidos",
            AccountClass.COST,
            501
    ),

    COST_OF_SERVICES(
            "Cost of Services",
            "Costo de Servicios",
            AccountClass.COST,
            502
    );

    private final String displayName;
    private final String displayNameEs;
    private final AccountClass accountClass;
    private final int displayOrder;

    AccountCategory(String displayName, String displayNameEs,
                    AccountClass accountClass, int displayOrder) {
        this.displayName = displayName;
        this.displayNameEs = displayNameEs;
        this.accountClass = accountClass;
        this.displayOrder = displayOrder;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDisplayNameEs() {
        return displayNameEs;
    }

    public AccountClass getAccountClass() {
        return accountClass;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    /**
     * Checks if this category belongs to the given account class.
     */
    public boolean belongsToClass(AccountClass accountClass) {
        return this.accountClass == accountClass;
    }

    /**
     * Returns all categories for a specific account class.
     */
    public static AccountCategory[] getCategoriesForClass(AccountClass accountClass) {
        return java.util.Arrays.stream(values())
                .filter(cat -> cat.belongsToClass(accountClass))
                .toArray(AccountCategory[]::new);
    }
}