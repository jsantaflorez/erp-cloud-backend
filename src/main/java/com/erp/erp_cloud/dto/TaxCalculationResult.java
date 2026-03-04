package com.erp.erp_cloud.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaxCalculationResult {
    private boolean isTaxable;
    private String taxName;
    private BigDecimal rate;
    private BigDecimal baseAmount;
    private BigDecimal taxAmount;
    private String sign; // D or C
    private Long accountId;
}