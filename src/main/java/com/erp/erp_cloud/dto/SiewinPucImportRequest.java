package com.erp.erp_cloud.dto;

import java.util.List;

/**
 * Request body for POST /api/v1/chart-of-accounts/import-siewin.
 *
 * rows is the entire legacy SIEWIN export, already parsed client-side into
 * SiewinPucRowDTO -- see that class's javadoc for why the parsing stops at
 * "raw source columns, one row each" rather than doing any transformation
 * in the browser.
 */
public record SiewinPucImportRequest(List<SiewinPucRowDTO> rows) {
}
