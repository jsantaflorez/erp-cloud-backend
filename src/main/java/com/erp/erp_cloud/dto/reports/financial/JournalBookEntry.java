package com.erp.erp_cloud.dto.reports.financial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Fase 4 (2026-11-24): one document of the Libro Diario page. Field names
 * match JournalEntryResponseDTO (and its items), so the screens that used
 * to read GET /journal-entries keep reading the same properties.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalBookEntry {

    private Long id;
    private String documentNumber;
    private Long consecutive;
    private Long documentTypeId;
    private LocalDate entryDate;
    private String description;
    private boolean annulled;
    private String annulmentReason;
    private Long reversalEntryId;
    private String reversalDocumentNumber;
    private Long reversalOfEntryId;
    private String reversalOfDocumentNumber;

    @Builder.Default
    private List<Line> items = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        private Long id;
        private String accountCode;
        private String accountName;
        private String thirdPartyIdNumber;
        private String thirdPartyName;
        private String costCenterName;
        private BigDecimal debit;
        private BigDecimal credit;
        private String description;
    }
}
