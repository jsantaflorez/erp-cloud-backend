package com.erp.erp_cloud.dto.reports.financial;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Fase 4 (2026-11-24): exact totals of the Libro Diario for the same filters
 * as GET /api/v1/reports/journal-lines, summed in the database and added in
 * BigDecimal. The client shows these (not its own float sums) and refuses
 * to show the book when the pages it received don't add up to them -- e.g.
 * a document was saved or edited while the pages were being read.
 *
 * Split by the Libro Diario status filter (Fase 3 rule): "annulled side" =
 * annulled documents plus the ANULACION reverses that cancel them; "active"
 * = everything else; "all" = both. Amounts are serialized as strings so no
 * precision is lost on the way to the browser.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalBookTotals {

    private Bucket all;
    private Bucket active;
    private Bucket annulled;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Bucket {
        private long entryCount;
        private long lineCount;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal debit;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal credit;

        public static Bucket empty() {
            return new Bucket(0, 0, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));
        }

        public Bucket plus(Bucket other) {
            return new Bucket(entryCount + other.entryCount, lineCount + other.lineCount,
                    debit.add(other.debit), credit.add(other.credit));
        }
    }
}
