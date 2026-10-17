package com.erp.erp_cloud.dto.reports.financial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Container for the "Informe Detallado de Impuestos y Retenciones"
 * (2026-10-16): the unified report covering every Tax category (IVA,
 * Retenciones, Otro/Impoconsumo/future taxes) in one screen, replacing
 * what would otherwise be a separate report per category.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxDetailReport {

    private String companyName;
    private String reportTitle;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime generatedAt;

    /** Filters actually applied, echoed back for the report header/export. */
    private String categoryFilter;
    private String taxTypeFilter;
    private String thirdPartyDocumentFilter;
    private String thirdPartyNameFilter;

    private List<TaxDetailLine> lines;
    private List<TaxTypeSubtotal> subtotalsByType;

    private BigDecimal totalBase;
    private BigDecimal totalValue;
    private int totalRecords;
}
