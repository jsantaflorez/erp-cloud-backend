package com.erp.erp_cloud.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * NEW (2026-10-03): request body for POST /{year}/close-year. Replaces
 * the old AccountingPeriodActionRequest (notes-only) for this one
 * endpoint specifically, since closing a year now also needs the
 * Ganancia/Pérdida accounts the user confirmed on screen for THIS
 * specific year -- see AccountingPeriod.gainAccount/lossAccount for why
 * these are never silently inherited from the company default.
 */
@Data
public class CloseYearRequest {

    @NotBlank(message = "Notes are required for audit trail")
    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;

    @NotNull(message = "gainAccountId is required")
    private Long gainAccountId;

    @NotNull(message = "lossAccountId is required")
    private Long lossAccountId;
}
