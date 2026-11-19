package com.erp.erp_cloud.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of a successful POST /api/v1/journal-entries/import-opening-balances-siewin.
 *
 * netResult follows the exact same sign convention as
 * AccountingPeriodService.buildAndSaveClosingEntry()'s own netResult:
 * positive = Utilidad (credited to Company.defaultGainAccount), negative
 * = Pérdida (debited to Company.defaultLossAccount), zero = no result
 * line was needed. See claude/analisis-migracion-saldos-siewin-2026-11-12.md,
 * section 1.3.
 *
 * replacedExistingEntry is true when this import annulled a previous
 * opening entry for the same company before creating the new one
 * (section 2.2's "reemplazo total" -- a re-upload during the Jan-Apr
 * window), false on a company's very first import.
 *
 * duplicateBlocksRemoved is informational only, not a warning that
 * blocked anything: it lists the exact-duplicate consecutive row blocks
 * the SIEWIN RPV v.6 export is known to sometimes produce (section 6.2),
 * which were detected and discarded automatically before any validation
 * or posting ran.
 */
public record SiewinBalanceImportResultDTO(
        String openingEntryDocumentNumber,
        int accountLinesPosted,
        int thirdPartyLinesPosted,
        BigDecimal netResult,
        boolean replacedExistingEntry,
        List<String> duplicateBlocksRemoved
) {
}
