package com.erp.erp_cloud.dto.reports.financial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Container for the "Informe de Movimientos Incompletos" (2026-10-20).
 * See IncompleteMovementLine's javadoc for what this audits and why.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncompleteMovementsReport {

    private String companyName;
    private String reportTitle;

    /** Echoed back for the report header -- null means "sin filtro", not "hoy". */
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime generatedAt;

    private List<IncompleteMovementLine> lines;
    private int totalRecords;
}
