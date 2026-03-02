package com.erp.erp_cloud.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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


    // Account info (avoid sending the entire ChartOfAccount entity)
    private Long accountId;
    private String accountCode;
    private String accountName;

    // Audit fields (optional, useful for debugging)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // UI Helpers
    // Formatted display field for dropdowns/UI
    private String fullDescription; // e.g., "IVA - 19% (D)"
    private String fullTaxDescription; // e.g., "IVA 19% (Account 240801)"
}