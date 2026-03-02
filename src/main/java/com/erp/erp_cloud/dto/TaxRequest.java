package com.erp.erp_cloud.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class TaxRequest {

    @NotBlank(message = "Tax code is required")
    @Size(max = 10, message = "Code must be up to 10 characters")
    private String code;

    @NotBlank(message = "Tax name is required")
    @Size(max = 100, message = "Name must be up to 100 characters")
    private String name;

    @NotBlank(message = "Tax type is required (IVA, RETE, etc.)")
    @Size(max = 20)
    private String type;

    @NotNull(message = "Tax rate is required")
    @DecimalMin(value = "0.0", message = "Rate cannot be negative")
    private BigDecimal rate;

    private boolean requiresBase = true;

    @DecimalMin(value = "0.0", message = "Minimum base cannot be negative")
    private BigDecimal minimumBase = BigDecimal.ZERO;

    @NotBlank(message = "Sign is required (D or C)")
    @Pattern(regexp = "[DC]", message = "Sign must be 'D' (Debit) or 'C' (Credit)")
    private String sign;

    @NotNull(message = "Accounting account ID is required")
    private Long accountId;

    private Boolean active;
}

