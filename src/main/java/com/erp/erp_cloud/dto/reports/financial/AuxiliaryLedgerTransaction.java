package com.erp.erp_cloud.dto.reports.financial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a single transaction line in the Auxiliary Ledger.
 *
 * Matches the format shown in the image:
 * FECHA | DOCUMENTO | DETALLE | DÉBITO | CRÉDITO | NUEVO SALDO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuxiliaryLedgerTransaction {

    /**
     * Transaction date (FECHA).
     * Example: "2026-01-02"
     */
    private LocalDate transactionDate;

    /**
     * Document number (DOCUMENTO).
     * Example: "CE 2056", "NC 203"
     */
    private String documentNumber;

    /**
     * Transaction detail/description (DETALLE).
     * Example: "*2056*PAGOS VARIOS AVANZA", "*NOTA CONTABLE AJUSTE POR SALI"
     */
    private String detail;

    /**
     * Debit amount (DÉBITO).
     * Empty if this is a credit transaction.
     */
    private BigDecimal debit;

    /**
     * Credit amount (CRÉDITO).
     * Empty if this is a debit transaction.
     */
    private BigDecimal credit;

    /**
     * Running balance after this transaction (NUEVO SALDO).
     *
     * Calculation:
     * - For Debit accounts: Previous Balance + Debit - Credit
     * - For Credit accounts: Previous Balance + Credit - Debit
     */
    private BigDecimal newBalance;

    /**
     * Third party document number (if applicable).
     * Used for filtering or additional detail.
     */
    private String thirdPartyDocument;

    /**
     * Third party name (if applicable).
     */
    private String thirdPartyName;

    /**
     * Cost center code (if applicable).
     */
    private String costCenterCode;

    // ═══════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════

    /**
     * Returns formatted date as shown in the image.
     * Example: "11-05-05-01" for opening balance row
     * Example: "2026-01-02" for transaction rows
     */
    public String getFormattedDate() {
        return transactionDate != null ? transactionDate.toString() : "";
    }

    /**
     * Returns formatted debit amount.
     * Returns empty string if zero (matching the image format).
     */
    public String getFormattedDebit() {
        if (debit == null || debit.compareTo(BigDecimal.ZERO) == 0) {
            return "";
        }
        return String.format("%,.2f", debit);
    }

    /**
     * Returns formatted credit amount.
     * Returns empty string if zero (matching the image format).
     */
    public String getFormattedCredit() {
        if (credit == null || credit.compareTo(BigDecimal.ZERO) == 0) {
            return "";
        }
        return String.format("%,.2f", credit);
    }

    /**
     * Returns formatted new balance.
     */
    public String getFormattedNewBalance() {
        if (newBalance == null) {
            return "0.00";
        }
        return String.format("%,.2f", newBalance);
    }

    /**
     * Checks if this is the opening balance row.
     */
    public boolean isOpeningBalance() {
        return "SALDO ANTERIOR".equals(detail);
    }

    /**
     * Checks if this is a debit transaction.
     */
    public boolean isDebit() {
        return debit != null && debit.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Checks if this is a credit transaction.
     */
    public boolean isCredit() {
        return credit != null && credit.compareTo(BigDecimal.ZERO) > 0;
    }
}
//```
//
//        ---
//
//        ## **📋 DTO Structure Matching the Image**
//        ```
//AuxiliaryLedgerReport
//├── Header Info:
//        │   ├── companyName: "ALMENDROS"
//        │   ├── reportTitle: "AUXILIAR POR CUENTA - RESUMIDO"
//        │   ├── startDate: "2026-01-01"
//        │   ├── endDate: "2026-01-08"
//        │   ├── generatedAt: "2026-03-26 17:03"
//        │   ├── accountCode: "11-05-05-0001-0000-00000"
//        │   └── accountName: "CAJA GENERAL"
//        │
//        ├── Opening Balance:
//        │   └── openingBalance: 20,775.00
//        │
//        ├── Transactions:
//        │   ├── AuxiliaryLedgerTransaction {
//│   │   ├── date: "2026-01-02"
//│   │   ├── documentNumber: "CE 2056"
//│   │   ├── detail: "*2056*PAGOS VARIOS AVANZA"
//│   │   ├── debit: 0.00
//│   │   ├── credit: 2,658.00
//│   │   └── newBalance: 18,117.00  ← 20,775.00 - 2,658.00
//│   │   }
//│   │
//        │   └── AuxiliaryLedgerTransaction {
//│       ├── date: "2026-01-02"
//│       ├── documentNumber: "NC 203"
//│       ├── detail: "*NOTA CONTABLE AJUSTE POR SALI"
//│       ├── debit: 0.00
//│       ├── credit: 136.00
//│       └── newBalance: 17,981.00  ← 18,117.00 - 136.00
//│       }
//│
//        ├── Totals:
//        │   ├── totalDebits: 0.00
//        │   ├── totalCredits: 2,794.00
//        │   ├── closingBalance: 17,981.00
//        │   └── totalRecords: 2
//        │
//        └── Control Totals:
//        ├── "TOTALES POR CUENTA": 20,775.00 | 0.00 | 2,794.00 | 17,981.00
//        └── "TOTAL REGISTROS": 2