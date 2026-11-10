package com.erp.erp_cloud.dto.certificates;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One row of the optional breakdown table inside a certificate's body
 * (2026-11-07) -- exposed to the Mustache template as the {{#lines}}
 * block, so a template can show a per-entry/per-month detail if the
 * accountant wants one, without the backend forcing a fixed layout.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithholdingCertificateLine {
    private String entryDate;
    private String documentNumber;
    private String detail;
    // 2026-11-10: concepto de la retención = nombre del impuesto del
    // catálogo (p. ej. "Retención por honorarios 10%"). Antes la tabla del
    // certificado mostraba "detail" (la descripción de la línea contable),
    // que suele traer el nombre del tercero, no el concepto.
    private String concept;
    private BigDecimal base;
    private BigDecimal rate;
    private BigDecimal value;
}
