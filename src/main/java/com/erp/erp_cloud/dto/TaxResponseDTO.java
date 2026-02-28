package com.erp.erp_cloud.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class TaxResponseDTO {
    private Long id;
    private String code;
    private String name;
    private String type;
    private BigDecimal rate;
    private boolean requiresBase;
    private BigDecimal minimumBase;
    private String sign;
    private boolean active;

    // UI Helpers
    private String accountCode;
    private String accountName;
    private String fullTaxDescription; // e.g., "IVA 19% (Account 240801)"
}