package com.erp.erp_cloud.dto.reports.financial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Fase 4 (2026-11-24): one page of GET /api/v1/reports/journal-lines.
 *
 * Keyset ("cursor") pagination over (entryDate, id): the next page is
 * requested with afterDate/afterId = nextAfterDate/nextAfterId. Unlike an
 * offset, the cursor never repeats or skips a document when others are
 * saved while the client walks the pages, and a document's lines never
 * split across two pages (the page is cut by documents, not by lines).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalBookPage {

    private List<JournalBookEntry> entries;
    private boolean hasMore;
    private LocalDate nextAfterDate;
    private Long nextAfterId;
}
