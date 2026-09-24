package com.erp.erp_cloud.dto.reports.financial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * One cost center's block in the "Auxiliar por Centro de Costo" report:
 * opening balance, its transactions (which can come from several
 * different accounts -- see AuxiliaryLedgerTransaction.accountCode), and
 * the resulting closing balance. Mirrors AuxiliaryAccountGroup, minus an
 * accountNature field: a cost center has no D/C nature of its own, since
 * its transactions can span accounts of different natures (each line's
 * own account nature is what the running balance calculation actually
 * uses -- see CostCenterReportService).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostCenterBalanceGroup {

    private String costCenterCode;
    private String costCenterName;

    private BigDecimal openingBalance;
    private List<AuxiliaryLedgerTransaction> transactions;

    private BigDecimal totalDebits;
    private BigDecimal totalCredits;
    private BigDecimal closingBalance;
    private Integer totalRecords;
}
