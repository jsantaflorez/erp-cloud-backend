package com.erp.erp_cloud.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Simplified request for action-based endpoints where year/month
 * are already provided in the URL path.
 */
@Data
public class AccountingPeriodActionRequest {

    @NotBlank(message = "Notes are required for audit trail")
    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;
}