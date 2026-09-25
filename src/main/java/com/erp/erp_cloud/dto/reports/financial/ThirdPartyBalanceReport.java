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
 * Container for the "Estado de Cuenta por Tercero" report: one block per
 * third party, each with its own opening balance, transactions and
 * closing balance -- same shape and live-calculation approach as
 * CostCenterBalanceReport, just grouped by third party instead, with an
 * optional account range and an optional cost center filter (null/blank
 * means every cost center, same convention as the Cost Center report's
 * own "TODOS LOS CENTROS DE COSTO").
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThirdPartyBalanceReport {

    private String companyName;
    private String reportTitle;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime generatedAt;

    // Range filter info
    private String startAccountCode;
    private String endAccountCode;
    private String thirdPartyDocument; // null = all third parties
    private String thirdPartyName;     // populated only when thirdPartyDocument is set
    private String costCenterCode;     // optional secondary filter; null = all cost centers
    private String costCenterName;     // populated only when costCenterCode is set

    private List<ThirdPartyBalanceGroup> thirdPartyGroups;

    public BigDecimal getTotalDebits() {
        return thirdPartyGroups == null ? BigDecimal.ZERO : thirdPartyGroups.stream()
                .map(g -> g.getTotalDebits() != null ? g.getTotalDebits() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalCredits() {
        return thirdPartyGroups == null ? BigDecimal.ZERO : thirdPartyGroups.stream()
                .map(g -> g.getTotalCredits() != null ? g.getTotalCredits() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public String getHeaderLine1() {
        return String.format("NOM. COMPAÑÍA: %-50s", companyName);
    }

    public String getHeaderLine2() {
        return String.format("TÍTULO: %-50s FECHA HORA REPORTE: %s",
                reportTitle != null ? reportTitle : "ESTADO DE CUENTA POR TERCERO",
                generatedAt != null ? generatedAt.toString().replace('T', ' ').substring(0, 16) : "");
    }

    public String getHeaderLine3() {
        return String.format("FECHA INICIAL: %-40s FECHA FINAL: %s", startDate, endDate);
    }

    public String getHeaderLine4() {
        return String.format("DESDE LA CUENTA: %-30s HASTA LA CUENTA: %-20s TERCERO: %s",
                startAccountCode != null ? startAccountCode : "",
                endAccountCode != null ? endAccountCode : "",
                thirdPartyDocument != null ? (thirdPartyDocument + " - " + thirdPartyName) : "TODOS LOS TERCEROS");
    }

    public String getHeaderLine5() {
        return String.format("CENTRO DE COSTO: %s",
                costCenterCode != null ? (costCenterCode + " - " + costCenterName) : "TODOS LOS CENTROS DE COSTO");
    }
}
