package com.erp.erp_cloud.dto;

import java.util.List;

/**
 * Result of a successful POST /api/v1/chart-of-accounts/import-siewin.
 *
 * truncatedNameWarnings: non-blocking, informational only -- see
 * SiewinPucImportService.looksTruncated() and
 * propuesta-importador-puc-siewin-2026-10-31.md section 4 ("Hallazgo
 * adicional"). These accounts WERE created; this list just flags which
 * ones have a name that looks cut off at the source (SIEWIN's own data),
 * so the accountant can review and complete them by hand afterwards
 * instead of finding out by accident later.
 */
public record SiewinPucImportResultDTO(
        int accountsCreated,
        int rootAccountsCreated,
        List<String> truncatedNameWarnings
) {
}
