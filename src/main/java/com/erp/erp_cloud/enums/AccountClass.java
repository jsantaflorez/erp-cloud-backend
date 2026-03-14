package com.erp.erp_cloud.enums;

/**
 * Main classification of accounts based on Colombian PUC (Plan Único de Cuentas).
 *
 * This represents the top-level grouping:
 * - Class 1: Assets (Activos)
 * - Class 2: Liabilities (Pasivos)
 * - Class 3: Equity (Patrimonio)
 * - Class 4: Revenue (Ingresos)
 * - Class 5: Expenses (Gastos)
 * - Class 6-7: Costs (Costos)
 */
public enum AccountClass {

    ASSET("Asset", "Activo", "1"),
    LIABILITY("Liability", "Pasivo", "2"),
    EQUITY("Equity", "Patrimonio", "3"),
    REVENUE("Revenue", "Ingreso", "4"),
    EXPENSE("Expense", "Gasto", "5"),
    COST("Cost", "Costo", "6");

    private final String displayName;
    private final String displayNameEs;
    private final String codePrefix;

    AccountClass(String displayName, String displayNameEs, String codePrefix) {
        this.displayName = displayName;
        this.displayNameEs = displayNameEs;
        this.codePrefix = codePrefix;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDisplayNameEs() {
        return displayNameEs;
    }

    public String getCodePrefix() {
        return codePrefix;
    }

    /**
     * Determines the account class from the account code.
     * Colombian PUC uses the first digit to classify accounts.
     *
     * @param code The account code
     * @return The corresponding AccountClass
     */
    public static AccountClass fromCode(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("Account code cannot be null or empty");
        }

        char firstDigit = code.charAt(0);

        return switch (firstDigit) {
            case '1' -> ASSET;
            case '2' -> LIABILITY;
            case '3' -> EQUITY;
            case '4' -> REVENUE;
            case '5' -> EXPENSE;
            case '6', '7' -> COST;
            default -> throw new IllegalArgumentException(
                    "Invalid account code prefix: " + firstDigit
            );
        };
    }
}