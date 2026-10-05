
package com.erp.erp_cloud.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountingPeriodResponseDTO {
    private Long id;
    private Integer year;
    private Integer month;
    private String periodCode; // e.g., "2026-01"
    private boolean isYearClose;
    private boolean isOpen;
    private LocalDateTime closedAt;
    private String closedBy;
    private String closingNotes;
    private LocalDateTime reopenedAt;
    private String reopenedBy;
    private String reopeningNotes;

    // NEW (2026-10-03): only populated on the month=12 record of a closed
    // year. See AccountingPeriod.gainAccount/lossAccount/closingEntry.
    private Long gainAccountId;
    private String gainAccountCode;
    private String gainAccountName;
    private Long lossAccountId;
    private String lossAccountCode;
    private String lossAccountName;
    private Long closingEntryId;
    private String closingEntryDocumentNumber;
}