package com.erp.erp_cloud.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Minimal read-only view of the current tenant's Company, for frontend
 * needs that don't warrant a full Company CRUD (there is none yet --
 * companies are provisioned directly in the database today). chartTemplate
 * is null when the company has no chart-of-accounts template configured
 * (the correct default; see ChartTemplateType).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyResponseDTO {
    private Long id;
    private String legalName;
    private String tradeName;
    private String chartTemplate; // null, "COMERCIAL" or "SOLIDARIO"
}
