package com.erp.erp_cloud.dto;

import java.util.List;

/**
 * Request body for POST /api/v1/third-parties/import-siewin.
 *
 * Same shape convention as SiewinPucImportRequest: the frontend parses
 * the uploaded .xlsx/.csv client-side (the `xlsx` library it already
 * depends on) and sends the rows as plain JSON, untouched -- no business
 * logic runs in the browser, only cell extraction.
 */
public record SiewinThirdPartyImportRequest(List<SiewinThirdPartyRowDTO> rows) {
}
