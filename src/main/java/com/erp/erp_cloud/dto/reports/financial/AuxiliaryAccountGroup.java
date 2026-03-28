package com.erp.erp_cloud.dto.reports.financial;

import com.erp.erp_cloud.enums.AccountNature;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuxiliaryAccountGroup {

    private String accountCode;
    private String accountName;
    private AccountNature accountNature;

    private BigDecimal openingBalance;
    private List<AuxiliaryLedgerTransaction> transactions;

    private BigDecimal totalDebits;
    private BigDecimal totalCredits;
    private BigDecimal closingBalance;
    private Integer totalRecords;

    /**
     * Logic for net movement calculation.
     */

    public BigDecimal getNetMovement() {
        BigDecimal debits = totalDebits != null ? totalDebits : BigDecimal.ZERO;
        BigDecimal credits = totalCredits != null ? totalCredits : BigDecimal.ZERO;

        return AccountNature.D.equals(accountNature)  // Enum comparison
                ? debits.subtract(credits)
                : credits.subtract(debits);
    }


    /**
     * Formats currency amounts for the group totals.
     *
     * * */
    public String formatAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return "";
        }
        return String.format("%,.2f", amount);
    }
}