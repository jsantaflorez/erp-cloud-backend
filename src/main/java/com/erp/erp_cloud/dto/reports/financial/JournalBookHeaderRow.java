package com.erp.erp_cloud.dto.reports.financial;

import java.time.LocalDate;

/**
 * Fase 4 (2026-11-24): one journal entry header, read with a JPQL
 * constructor projection (no entity hydration) for the paginated
 * Libro Diario endpoint (GET /api/v1/reports/journal-lines).
 */
public record JournalBookHeaderRow(
        Long id,
        LocalDate entryDate,
        String documentNumber,
        Long consecutive,
        Long documentTypeId,
        String description,
        Boolean annulled,
        String annulmentReason,
        Long reversalEntryId,
        String reversalDocumentNumber,
        Long reversalOfEntryId,
        String reversalOfDocumentNumber) {
}
