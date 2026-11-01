package com.erp.erp_cloud.dto;

import java.util.List;

/**
 * Result of a successful POST /api/v1/third-parties/import-siewin.
 *
 * phonePlaceholderWarnings: non-blocking, informational only -- see
 * SiewinThirdPartyImportService.normalizePhone() and the proposal's
 * "Hallazgos no bloqueantes" section. These third parties WERE created
 * (with phone left empty); this list just flags which ones had a
 * placeholder value ("*", "0") in the source instead of a real number,
 * so the accountant can review and complete them by hand afterwards.
 */
public record SiewinThirdPartyImportResultDTO(
        int thirdPartiesCreated,
        List<String> phonePlaceholderWarnings
) {
}
