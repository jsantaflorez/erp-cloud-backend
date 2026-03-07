package com.erp.erp_cloud.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class TrialBalanceLine {
    private String accountCode;
    private String accountName;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    public BigDecimal getNetBalance() {
        return totalDebit.subtract(totalCredit);
    }

}

