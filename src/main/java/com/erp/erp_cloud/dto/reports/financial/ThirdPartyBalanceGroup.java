package com.erp.erp_cloud.dto.reports.financial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * One third party's block in the "Estado de Cuenta por Tercero" report:
 * opening balance, its transactions (which can come from several
 * different accounts and cost centers -- see
 * AuxiliaryLedgerTransaction.accountCode/costCenterCode), and the
 * resulting closing balance. Unlike CostCenterBalanceGroup, this is
 * NOT nature-aware: a third party's balance is always "opening balance
 * + debits - credits", regardless of which account (expense, revenue,
 * tax, cartera...) a given line posted to -- it behaves like a
 * receivable/payable subledger, not a resource-flow aggregation (see
 * ThirdPartyReportService.processRunningBalance()).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThirdPartyBalanceGroup {

    private String thirdPartyDocument;
    private String thirdPartyName;

    private BigDecimal openingBalance;
    private List<AuxiliaryLedgerTransaction> transactions;

    private BigDecimal totalDebits;
    private BigDecimal totalCredits;
    private BigDecimal closingBalance;
    private Integer totalRecords;
}
