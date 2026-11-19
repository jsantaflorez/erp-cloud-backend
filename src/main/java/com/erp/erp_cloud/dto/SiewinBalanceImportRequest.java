package com.erp.erp_cloud.dto;

import java.util.List;

/**
 * Request body for POST /api/v1/journal-entries/import-opening-balances-siewin.
 *
 * closingYear is the fiscal year whose December 31 closing balance this
 * file represents (e.g. 2026 for "PERIODO: 2026 DICIEMBRE") -- selected
 * explicitly by the accountant in the upload screen, never inferred from
 * the file itself: the real SIEWIN export carries no company/period
 * metadata at all (see claude/analisis-migracion-saldos-siewin-2026-11-12.md,
 * section 6.4). The resulting opening entry is always dated December 31
 * of this year, same convention as the year-end closing entry
 * (AccountingPeriodService.buildAndSaveClosingEntry()).
 *
 * rows is the entire file, already parsed client-side into
 * SiewinBalanceRowDTO -- see that class's javadoc for why the parsing
 * stops at "raw cell text, one row each" rather than any classification.
 */
public record SiewinBalanceImportRequest(Integer closingYear, List<SiewinBalanceRowDTO> rows) {
}
