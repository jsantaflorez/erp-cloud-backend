
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
}