package com.erp.erp_cloud.dto.certificates;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Feeds the 3 dropdowns of the Withholding Certificate screen
 * (2026-11-07). `thirdParties` is only populated when the request
 * included a taxType -- the frontend calls this endpoint twice: once
 * with just taxYear (to get taxTypes), then again with taxType added
 * once the accountant picks one (to get the matching third parties).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithholdingCertificateParametersDTO {
    private List<String> taxTypes;
    private List<ThirdPartyOptionDTO> thirdParties;
}
