package com.erp.erp_cloud.dto.reports.financial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Subtotal for one Tax type (e.g. "RETEFUENTE", "IVA_GENERADO",
 * "IMPOCONSUMO") within the "Informe Detallado de Impuestos y
 * Retenciones" -- what the accountant actually declares/certifies
 * separately per type, regardless of which category it belongs to.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxTypeSubtotal {

    private String taxCategory;
    private String taxType;
    private int recordCount;
    private BigDecimal totalBase;
    private BigDecimal totalValue;
}
