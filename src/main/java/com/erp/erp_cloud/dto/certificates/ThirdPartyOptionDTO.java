package com.erp.erp_cloud.dto.certificates;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of the "Tercero" dropdown on the Withholding Certificate
 * screen (2026-11-07) -- only third parties with at least one
 * RETENCION-category line in the selected tax year/type appear here,
 * see WithholdingCertificateService.getThirdPartiesWithRetentionInYear().
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThirdPartyOptionDTO {
    private String documentNumber;
    private String name;
}
