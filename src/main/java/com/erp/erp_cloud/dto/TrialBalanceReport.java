package com.erp.erp_cloud.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrialBalanceReport {
    private List<TrialBalanceLine> lines;
    private BigDecimal grandTotalDebit;
    private BigDecimal grandTotalCredit;
    private boolean isBalanced;

    private Map<String, BigDecimal> summaryByType;

    }
