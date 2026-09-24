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
 * Container for the "Auxiliar por Centro de Costo" report: one block per
 * cost center, each with its own opening balance, transactions and
 * closing balance -- same shape and live-calculation approach as
 * AuxiliaryLedgerReport (grouped by account), just grouped by cost
 * center instead, with an optional cost center filter (null/blank means
 * every cost center, same as "TODOS LOS CENTROS DE COSTO" in the
 * reference report the user provided).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostCenterBalanceReport {

    private String companyName;
    private String reportTitle;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime generatedAt;

    // Range filter info
    private String startAccountCode;
    private String endAccountCode;
    private String costCenterCode;   // null = all cost centers
    private String costCenterName;   // populated only when costCenterCode is set

    private List<CostCenterBalanceGroup> costCenterGroups;

    public BigDecimal getTotalDebits() {
        return costCenterGroups == null ? BigDecimal.ZERO : costCenterGroups.stream()
                .map(g -> g.getTotalDebits() != null ? g.getTotalDebits() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalCredits() {
        return costCenterGroups == null ? BigDecimal.ZERO : costCenterGroups.stream()
                .map(g -> g.getTotalCredits() != null ? g.getTotalCredits() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public String getHeaderLine1() {
        return String.format("NOM. COMPAÑÍA: %-50s", companyName);
    }

    public String getHeaderLine2() {
        return String.format("TÍTULO: %-50s FECHA HORA REPORTE: %s",
                reportTitle != null ? reportTitle : "AUXILIAR POR CENTRO DE COSTO",
                generatedAt != null ? generatedAt.toString().replace('T', ' ').substring(0, 16) : "");
    }

    public String getHeaderLine3() {
        return String.format("FECHA INICIAL: %-40s FECHA FINAL: %s", startDate, endDate);
    }

    public String getHeaderLine4() {
        return String.format("DESDE LA CUENTA: %-30s HASTA LA CUENTA: %-20s CENTRO DE COSTO: %s",
                startAccountCode != null ? startAccountCode : "",
                endAccountCode != null ? endAccountCode : "",
                costCenterCode != null ? (costCenterCode + " - " + costCenterName) : "TODOS LOS CENTROS DE COSTO");
    }
}
