package com.erp.erp_cloud.dto;

import java.math.BigDecimal;

/**
 * One row of the SIEWIN RPV v.6 opening-balance export ("Libro de
 * Inventario y Balance de Cierre"), exactly as parsed client-side from the
 * malformed Excel-HTML file SIEWIN produces, before being sent to
 * POST /api/v1/journal-entries/import-opening-balances-siewin.
 *
 * Same philosophy as SiewinPucRowDTO: the browser only extracts raw cell
 * text (decoding the Latin-1/ISO-8859-1 bytes and stripping the HTML
 * markup), it never decides whether a row is an account, a third-party
 * breakdown line, or a stray "TOTAL :" marker -- that classification needs
 * to cross-reference the company's own already-migrated Plan de Cuentas
 * and Terceros catalogs, so it belongs in SiewinBalanceImportService, not
 * in the browser. See claude/analisis-migracion-saldos-siewin-2026-11-12.md,
 * section 6.3.
 *
 * rawCode is the row's first cell: an account code ("23-65-40-01"), a
 * third-party document number ("800197268"), or literal "TOTAL : -20.462"
 * text for the sparse/unreliable subtotal marker rows -- the backend drops
 * those (see section 6.3: they don't appear consistently enough to rely on
 * as a group boundary) and the frontend is expected to filter them out
 * before sending too, though the backend never assumes that happened.
 *
 * amount is already parsed to a BigDecimal (raw debit-minus-credit
 * convention: positive for a debit-normal balance, negative for a
 * credit-normal one -- exactly how SIEWIN itself prints it) from whichever
 * of the file's several depth-indented amount columns actually held a
 * value for that row; null for a row with no amount at all (a bare
 * "TOTAL :" marker, or a blank separator line).
 *
 * rowIndex is the row's 0-based position in the original file (after the
 * frontend's own HTML-row extraction, before any filtering), kept only so
 * a validation error can point the accountant at an approximate row to go
 * check in their own copy of the file.
 */
public record SiewinBalanceRowDTO(
        int rowIndex,
        String rawCode,
        String name,
        BigDecimal amount
) {
}
