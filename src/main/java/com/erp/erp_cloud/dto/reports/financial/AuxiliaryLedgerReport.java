package com.erp.erp_cloud.dto.reports.financial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Global Container for the Auxiliary Ledger Report.
 * Supports a range of accounts and a range of dates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuxiliaryLedgerReport {

    private String companyName;
    private String reportTitle;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime generatedAt;

    // Range filter info
    private String startAccountCode;
    private String endAccountCode;

    private Integer pageNumber;
    private Integer totalPages;

    /**
     * The list of account blocks.
     * Each block contains its own opening balance and transactions.
     */
    private List<AuxiliaryAccountGroup> accountGroups;

    // ═══════════════════════════════════════════════════════════
    //  METHODS (GLOBAL)
    // ═══════════════════════════════════════════════════════════

    public String getHeaderLine1() {
        return String.format("NOM. COMPAÑÍA: %-50s PÁGINA: %d DE %d PÁGINAS",
                companyName, pageNumber != null ? pageNumber : 1, totalPages != null ? totalPages : 1);
    }

    public String getHeaderLine2() {
        return String.format("TÍTULO: %-50s FECHA HORA REPORTE: %s",
                reportTitle != null ? reportTitle : "AUXILIAR POR CUENTA - RESUMIDO",
                generatedAt != null ? generatedAt.toString().replace('T', ' ').substring(0, 16) : "");
    }

    public String getHeaderLine3() {
        return String.format("FECHA INICIAL: %-40s FECHA FINAL: %s", startDate, endDate);
    }

    public String getHeaderLine4() {
        return String.format("DESDE LA CUENTA: %-40s HASTA LA CUENTA: %s",
                startAccountCode != null ? startAccountCode : "",
                endAccountCode != null ? endAccountCode : "");
    }
}